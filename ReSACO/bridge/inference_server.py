"""TCP bridge that lets the Java EdgeCloudSim orchestrator use trained
ReSACO/baseline policies for live offloading decisions.

Serves all five algorithms compared in Section V-C of the paper at once,
each loaded from its own checkpoint and selected per-request by name --
this lets EdgeCloudSim run ReSACO, SAC (no meta-init), DDPG, A2C and A3C
as five separate `orchestrator_policies` in the *same* real CloudSim
simulation (scripts/ReSACO), instead of only the toy env used by
scripts/compare_algorithms.py.

Online-learning persistence: RESACO/SAC_BASELINE/DDPG_BASELINE keep
adapting theta_adapt from every reported outcome (Algorithm 4). Without
saving that back to disk, all of it is lost the instant this process
restarts -- so instead of overwriting the original meta-trained checkpoint
(theta_star.pt etc, which stays untouched as a stable fallback), each
adapting agent periodically flushes its live parameters to a separate
"<checkpoint>_adapted.pt" file (every --autosave-every updates, plus once
more on a clean shutdown). On the *next* startup, that adapted file is
preferred over the original if present, so online adaptation actually
accumulates across restarts instead of resetting every time. A2C/A3C are
served frozen (see FrozenPolicyAgent) and never have anything to persist.

Protocol (newline-delimited ASCII, one request per line):

  ACT <algo> <request_id> <L> <U> <D> <mu_d> <mu_e1> ... <mu_eN> <mu_c> <bwlan> <bman> <bwan>
      -> "<action_int>"
         action in {0..N+1}: 0=device, 1..N=edge server index, N+1=cloud

  OUTCOME <algo> <request_id> <reward> <done:0|1> <next_state...>
      -> "OK" | "IGNORED"
         IGNORED means request_id was never seen by ACT for this algo (e.g.
         the bridge was unreachable/restarted at decision time). ReSACO/SAC/
         DDPG (off-policy) turn this into an online SAC-Update/DDPG-update
         step (Algorithm 4); A2C/A3C (on-policy) just discard it -- their
         served policy is exactly what scripts/train_baselines.py produced.

  RESET <algo>
      -> "OK" | "ERROR ..."
         Algorithm 4 line 1 for a new scenario: re-copies the originally
         loaded checkpoint into theta_adapt and clears the replay buffer +
         in-flight state, so each EdgeCloudSim run adapts from theta*
         instead of inheriting the previous run's drift. The Java
         orchestrator sends this once per simulation run at initialize().

  PING
      -> "PONG"

<algo> is one of RESACO, SAC_BASELINE, DDPG_BASELINE, A2C_BASELINE,
A3C_BASELINE. If that algorithm's checkpoint wasn't found at startup, ACT
returns "ERROR unknown algo ..." and the Java client falls back to its
static heuristic for that decision -- same as if the whole bridge were
unreachable.

A missing/unreachable server should never crash the simulator: the Java
client (ReSACOBridgeClient) falls back to a static policy on any I/O error.
"""

import argparse
import os
import signal
import socketserver
import sys
import threading

import torch

_RESACO_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _RESACO_DIR)                      # ReSACO/  -> `resaco`, `bridge`
sys.path.insert(0, os.path.dirname(_RESACO_DIR))     # repo root -> `mec_core`, `baselines`

from mec_core import config
from resaco.deploy import DeploymentAgent, FrozenPolicyAgent
from resaco.sac import SACAgent
from baselines.ddpg import DDPGAgent
from baselines.a2c import A2CAgent

# name -> (checkpoint filename, agent factory, wrapper factory, persist)
# persist=True means the wrapper is a DeploymentAgent that keeps adapting
# theta_adapt online and needs its own "<checkpoint>_adapted.pt" save slot;
# False means a FrozenPolicyAgent (A2C/A3C), which never changes at runtime
# and so has nothing to persist.
ALGO_REGISTRY = {
    "RESACO": ("theta_star.pt", SACAgent, DeploymentAgent, True),
    "SAC_BASELINE": ("sac_no_meta.pt", SACAgent, DeploymentAgent, True),
    "DDPG_BASELINE": ("ddpg.pt", DDPGAgent, DeploymentAgent, True),
    "A2C_BASELINE": ("a2c.pt", A2CAgent, FrozenPolicyAgent, False),
    "A3C_BASELINE": ("a3c.pt", A2CAgent, FrozenPolicyAgent, False),
}

_agents = {}  # algo name -> DeploymentAgent | FrozenPolicyAgent
# One lock per algo, not one global lock -- each algo's DeploymentAgent/
# FrozenPolicyAgent only ever touches its own independent SACAgent/
# DDPGAgent/A2CAgent state (own networks, own replay buffer, own _pending
# dict), so nothing needs protecting *across* algos. A single shared lock
# would otherwise serialize every algo's ACT/OUTCOME behind, say, RESACO's
# training step or its autosave's blocking torch.save() -- a request for
# SAC_BASELINE has no reason to wait on that.
_locks = {algo: threading.Lock() for algo in ALGO_REGISTRY}


def _parse_floats(tokens):
    return [float(t) for t in tokens]


class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        while True:
            try:
                raw = self.rfile.readline()
            except OSError:
                break
            if not raw:
                break
            line = raw.decode("utf-8").strip()
            if not line:
                continue
            try:
                response = self._dispatch(line)
            except Exception as exc:  # never let a bad request kill the server
                response = f"ERROR {exc}"
            try:
                self.wfile.write((response + "\n").encode("utf-8"))
            except OSError:
                break

    def _dispatch(self, line: str) -> str:
        parts = line.split()
        cmd = parts[0].upper()

        if cmd == "PING":
            return "PONG"

        if cmd == "ACT":
            algo, request_id = parts[1], parts[2]
            agent = _agents.get(algo)
            if agent is None:
                return f"ERROR unknown algo {algo}"
            state = _parse_floats(parts[3:])
            if len(state) != config.STATE_DIM:
                return f"ERROR expected {config.STATE_DIM} state values, got {len(state)}"
            with _locks[algo]:
                action = agent.select_action(state, request_id=request_id)
            return str(action)

        if cmd == "OUTCOME":
            algo, request_id = parts[1], parts[2]
            agent = _agents.get(algo)
            if agent is None:
                return f"ERROR unknown algo {algo}"
            reward = float(parts[3])
            done = bool(int(parts[4]))
            next_state = _parse_floats(parts[5:])
            with _locks[algo]:
                result = agent.report_outcome(request_id, reward, next_state, done)
            # result is None only when request_id was never seen by select_action
            # (e.g. the bridge was unreachable/restarted at decision time).
            return "OK" if result is not None else "IGNORED"

        if cmd == "RESET":
            algo = parts[1] if len(parts) > 1 else None
            if algo is None:
                return "ERROR RESET requires an algo name"
            agent = _agents.get(algo)
            if agent is None:
                return f"ERROR unknown algo {algo}"
            with _locks[algo]:
                ok = agent.reset()
            # Algorithm 4 line 1 for a new scenario S_new: theta_adapt is
            # re-copied from the originally loaded parameter and the replay
            # buffer/pending state dropped, so each simulation run adapts
            # from theta* instead of inheriting the previous run's drift.
            return "OK" if ok else "ERROR nothing to reset (no checkpoint was loaded for this algo)"

        if cmd == "SAVE":
            algo = parts[1] if len(parts) > 1 else None
            path = parts[2] if len(parts) > 2 else None
            if algo is None:
                saved = save_all_agents()
                return f"OK {' '.join(saved)}" if saved else "OK none"
            agent = _agents.get(algo)
            if agent is None:
                return f"ERROR unknown algo {algo}"
            with _locks[algo]:
                if path:
                    torch.save(agent.state_dict(), path)
                elif not agent.save():
                    return "ERROR no save_path configured for this algo -- pass an explicit path"
            return "OK"

        return f"ERROR unknown command {cmd}"


class ThreadingTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


def _adapted_path(original_path: str) -> str:
    root, ext = os.path.splitext(original_path)
    return f"{root}_adapted{ext}"


def save_all_agents():
    """Flushes every agent's theta_adapt to its configured save_path (a
    no-op for algos with none, i.e. FrozenPolicyAgent-served A2C/A3C).
    Used by the SAVE-with-no-args protocol command and on shutdown.

    Each algo's save is guarded by its own lock, not one lock held for the
    whole loop -- a concurrent ACT/OUTCOME for an algo not currently being
    saved only ever waits on that algo's own (free) lock, not on however
    long every other algo's save takes.
    """
    saved = []
    for algo, agent in _agents.items():
        with _locks[algo]:
            if agent.save():
                saved.append(algo)
    return saved


def load_agents(checkpoints_dir: str, autosave_every: int = 50):
    """Loads each algo's checkpoint, preferring a prior online-adapted
    checkpoint ("<checkpoint>_adapted.pt") over the original meta-trained
    one if it exists, so accumulated online learning (Algorithm 4) survives
    a bridge restart instead of resetting to theta_star every time. Persist-
    capable agents (DeploymentAgent) are wired with save_path pointing at
    that adapted file so future adaptation keeps accumulating there; the
    original checkpoint itself is never overwritten.
    """
    loaded, resumed, missing = [], [], []
    for algo, (filename, agent_cls, wrapper_cls, persist) in ALGO_REGISTRY.items():
        original_path = os.path.join(checkpoints_dir, filename)
        adapted_path = _adapted_path(original_path)
        agent = agent_cls()

        wrapper_kwargs = {}
        if persist:
            wrapper_kwargs = {"save_path": adapted_path, "autosave_every": autosave_every}

        if persist and os.path.exists(adapted_path):
            load_path, bucket = adapted_path, resumed
        elif os.path.exists(original_path):
            load_path, bucket = original_path, loaded
        else:
            load_path, bucket = None, missing

        if load_path:
            params = torch.load(load_path, map_location="cpu")
            _agents[algo] = wrapper_cls(agent, params, **wrapper_kwargs)
        else:
            # serve a randomly-initialized (untrained) policy rather than
            # refusing to serve the algo at all -- keeps the simulation
            # runnable even before all baselines are trained, at the cost
            # of that algo's decisions being meaningless until retrained.
            _agents[algo] = wrapper_cls(agent, None, **wrapper_kwargs)
        bucket.append(algo)
    return loaded, resumed, missing


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoints-dir", type=str, default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "checkpoints"))
    parser.add_argument("--host", type=str, default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--autosave-every", type=int, default=50,
                         help="Flush online-adapted checkpoints to disk every N successful "
                              "updates (RESACO/SAC_BASELINE/DDPG_BASELINE only). Also saved "
                              "once more on a clean shutdown.")
    args = parser.parse_args()

    loaded, resumed, missing = load_agents(args.checkpoints_dir, autosave_every=args.autosave_every)
    if resumed:
        print(f"Resumed online-adapted checkpoints for: {', '.join(resumed)}")
    if loaded:
        print(f"Loaded trained checkpoints for: {', '.join(loaded)}")
    if missing:
        print(f"WARNING: no checkpoint found for {', '.join(missing)} in {args.checkpoints_dir} "
              f"-- serving randomly-initialized (untrained) policies for them. "
              f"Run train_meta.py / train_baselines.py first.")

    # SIGTERM has no default Python handler (unlike SIGINT/Ctrl+C, which
    # already raises KeyboardInterrupt) -- without this, a `kill`/service-stop
    # would drop straight through and skip the shutdown save below.
    def _raise_keyboard_interrupt(signum, frame):
        raise KeyboardInterrupt

    if hasattr(signal, "SIGTERM"):
        signal.signal(signal.SIGTERM, _raise_keyboard_interrupt)

    server = ThreadingTCPServer((args.host, args.port), Handler)
    print(f"ReSACO inference/online-learning bridge listening on {args.host}:{args.port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        print("Shutting down -- saving all online-adapted checkpoints...")
        saved = save_all_agents()
        print(f"Saved: {', '.join(saved)}" if saved else "Nothing to save.")


if __name__ == "__main__":
    main()
