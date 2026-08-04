"""DDPG/A2C/A3C used to blindly reuse SAC's ACTOR_LR/CRITIC_LR (see
ReSACO/README.md's "Convergence" section) -- these check each baseline
actually uses its own tuned learning rate, plus a light training smoke
test for each (construction + a short train_loop doesn't error)."""

from mec_core import config
from mec_core.env import MECOffloadEnv
from baselines.a2c import A2CAgent
from baselines.a3c import A3CTrainer
from baselines.ddpg import DDPGAgent
from mec_core.scenario import AppProfile, Scenario


def _make_scenario():
    profile = AppProfile(
        name="TEST_APP", usage_percentage=100.0, poisson_interarrival=8.0,
        delay_sensitivity=0.5, active_period=30.0, idle_period=30.0,
        data_upload=200.0, data_download=200.0, task_length=3000.0, required_core=1,
        vm_utilization_on_edge=10.0, vm_utilization_on_cloud=1.0, vm_utilization_on_mobile=10.0,
    )
    return Scenario(app_profile=profile, number_of_mobile_devices=500)


def test_ddpg_uses_its_own_learning_rates_not_sacs():
    agent = DDPGAgent()
    assert agent.actor_optim.param_groups[0]["lr"] == config.DDPG_ACTOR_LR
    assert agent.critic_optim.param_groups[0]["lr"] == config.DDPG_CRITIC_LR
    assert config.DDPG_ACTOR_LR != config.ACTOR_LR or config.DDPG_CRITIC_LR != config.CRITIC_LR


def test_a2c_uses_its_own_learning_rate_not_sacs():
    agent = A2CAgent()
    assert agent.actor_optim.param_groups[0]["lr"] == config.A2C_LR
    assert agent.critic_optim.param_groups[0]["lr"] == config.A2C_LR


def test_a3c_trainer_uses_a2c_learning_rate():
    trainer = A3CTrainer()
    assert trainer.actor_optim.param_groups[0]["lr"] == config.A2C_LR
    assert trainer.critic_optim.param_groups[0]["lr"] == config.A2C_LR


def test_ddpg_train_loop_runs_and_produces_updates():
    env = MECOffloadEnv(_make_scenario(), seed=1)
    agent = DDPGAgent(epsilon_decay_steps=100)
    stats = agent.train_loop(env, num_transitions=config.BATCH_SIZE + 10)
    assert len(stats) > 0


def test_a2c_train_loop_runs_and_produces_updates():
    env = MECOffloadEnv(_make_scenario(), seed=2)
    agent = A2CAgent(rollout_len=5)
    stats = agent.train_loop(env, num_transitions=25)
    assert len(stats) == 5
