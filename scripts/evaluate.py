import argparse
import os
import subprocess
import tarfile
import shutil
import sys
from datetime import datetime
from functools import partial
from natsort import natsorted
import numpy as np
import pandas as pd
import matplotlib

# This script only ever calls plt.savefig(), never plt.show() -- force the
# non-interactive Agg backend so it works headless (CI, SSH, no DISPLAY)
# instead of depending on matplotlib's own auto-detection picking one.
# Must come before importing pyplot, which locks the backend in on import.
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import scienceplots

plt.style.use(['science', 'ieee', 'no-latex'])

# index_mapping.py lives right next to this script.
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from index_mapping import all_apps_generic

###############################################################################
# Global folder name constants
###############################################################################
LOGS_SUBFOLDER_NAME = "logs"
GRAPH_SUBFOLDER_NAME = "graph"
EVALUATION_RESULT_DIRNAME = "evaluation_result"  # scripts/evaluation_result/<name>_<YYYYMMDD>/

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))  # scripts/


###############################################################################
# 0. Evaluation menu: number -> (display name, handler(results_dir))
###############################################################################
# EVALUATION_MENU itself is built at the bottom of this file (it needs
# run_app_evaluation, defined near main(), to already exist) -- see there
# for how new entries get added.
def prompt_for_evaluation_choice(cli_arg=None):
    """Returns the chosen EVALUATION_MENU key. If cli_arg is given (menu
    number or display name, case-insensitive), it's used instead of
    prompting -- so `python evaluate.py ReSACO` / `python evaluate.py 1`
    both skip the interactive menu, while plain `python evaluate.py` shows it."""
    if cli_arg:
        if cli_arg in EVALUATION_MENU:
            return cli_arg
        for key, entry in EVALUATION_MENU.items():
            if entry["name"].lower() == cli_arg.lower():
                return key
        options = ', '.join(f"{k} ({v['name']})" for k, v in EVALUATION_MENU.items())
        print(f"Error: '{cli_arg}' is not a valid choice. Options: {options}")
        sys.exit(1)

    print("실행할 평가 방식을 선택하세요:")
    for key, entry in EVALUATION_MENU.items():
        print(f"{key}. {entry['name']}")
    while True:
        choice = input("입력 (번호): ").strip()
        if choice in EVALUATION_MENU:
            return choice
        print("잘못된 입력입니다. 다시 선택해주세요.")


###############################################################################
# 1. Select date folder
###############################################################################
def get_available_date_folders(base_path):
    try:
        date_folders = [f for f in os.listdir(base_path) if os.path.isdir(os.path.join(base_path, f))]
        if not date_folders:
            print(f"Error: No date folders found in {base_path}.")
            sys.exit(1)
        return natsorted(date_folders)
    except Exception as e:
        print(f"Error: Unable to read {base_path} directory: {e}")
        sys.exit(1)


def select_date_folder(date_folders, auto=False):
    if auto:
        latest = date_folders[-1]
        print(f"[--auto] Using latest date folder: {latest}")
        return latest

    print("\nAvailable Date Folders:")
    print("-" * 50)
    print("0. (Auto) Use Latest Date")

    for idx, date in enumerate(date_folders, start=1):
        print(f"{idx}. {date}")

    print("-" * 50)

    while True:
        try:
            selection = int(input("Select a date by number: ")) - 1
            if selection == -1:
                return date_folders[-1]  # Automatically select the latest date
            elif 0 <= selection < len(date_folders):
                return date_folders[selection]
            else:
                print("Invalid selection. Try again.")
        except ValueError:
            print("Please enter a valid number.")


def get_available_scenario_folders(base_path, selected_date):
    """Scenario (simulation.list entry) folders under one date folder --
    e.g. default_config, high_mobility, congested_wan, weak_edge for
    ReSACO. Older runs only ever contain default_config."""
    date_dir = os.path.join(base_path, selected_date)
    try:
        return natsorted([f for f in os.listdir(date_dir)
                          if os.path.isdir(os.path.join(date_dir, f))])
    except Exception as e:
        print(f"Error: Unable to read {date_dir} directory: {e}")
        sys.exit(1)


def select_scenario_folder(scenario_folders, auto=False):
    """Pick which scenario folder to evaluate. Auto mode (and the
    single-scenario case) prefers default_config to keep unattended runs
    evaluating the same baseline scenario they always did."""
    if not scenario_folders:
        return "default_config"  # let the caller fail with its usual error path
    if len(scenario_folders) == 1:
        return scenario_folders[0]
    if auto:
        chosen = "default_config" if "default_config" in scenario_folders else scenario_folders[0]
        print(f"[--auto] Using scenario folder: {chosen}")
        return chosen

    print("\nAvailable Scenario Folders:")
    print("-" * 50)
    print("0. (Auto) Use default_config" if "default_config" in scenario_folders
          else "0. (Auto) Use first scenario")
    for idx, name in enumerate(scenario_folders, start=1):
        print(f"{idx}. {name}")
    print("-" * 50)

    while True:
        try:
            selection = int(input("Select a scenario by number: ")) - 1
            if selection == -1:
                return ("default_config" if "default_config" in scenario_folders
                        else scenario_folders[0])
            elif 0 <= selection < len(scenario_folders):
                return scenario_folders[selection]
            else:
                print("Invalid selection. Try again.")
        except ValueError:
            print("Please enter a valid number.")


###############################################################################
# 2. Create 'logs' and 'graph' folders under the resolved results directory
###############################################################################
def create_result_structure(results_dir):
    """
    Creates the following folder structure inside `results_dir` (already
    resolved to scripts/evaluation_result/<name>_<YYYYMMDD>/ by main()):
      logs/
      graph/
    If they already exist (e.g. re-running the same evaluation choice again
    on the same day), they're removed and recreated so stale files don't linger.
    """
    logs_dir = os.path.join(results_dir, LOGS_SUBFOLDER_NAME)
    graph_dir = os.path.join(results_dir, GRAPH_SUBFOLDER_NAME)

    if os.path.exists(logs_dir):
        shutil.rmtree(logs_dir)
    os.makedirs(logs_dir, exist_ok=True)

    if os.path.exists(graph_dir):
        shutil.rmtree(graph_dir)
    os.makedirs(graph_dir, exist_ok=True)

    return logs_dir, graph_dir


def remove_progress_folder(date_logs_dir):
    """
    If there is a 'progress' folder, remove it.
    """
    progress_dir = os.path.join(date_logs_dir, 'progress')
    if os.path.exists(progress_dir):
        shutil.rmtree(progress_dir)


###############################################################################
# 3. Extract .tar.gz and categorize logs
###############################################################################
def extract_and_categorize_tar(file_path, output_dir):
    """
    Extracts a .tar.gz file into 'output_dir', then categorizes .log files
    by parsing their filenames to get policy_name and category_name.
    """
    try:
        with tarfile.open(file_path, 'r:gz') as tar:
            tar.extractall(path=output_dir)

        for root, _, files in os.walk(output_dir):
            for file in files:
                if file.endswith('.log'):
                    parts = file.split('_')
                    # Parse policy_name and category_name
                    policy_name = next((
                        '_'.join(parts[i + 1:j])
                        for i, part in enumerate(parts) if part == 'TIER'
                        for j in range(i + 1, len(parts)) if 'DEVICES' in parts[j]
                    ), None)
                    category_name = next((
                        '_'.join(parts[j + 1:]).replace('.log', '')
                        for j in range(len(parts)) if 'DEVICES' in parts[j]
                    ), None)

                    if policy_name and category_name:
                        policy_dir = os.path.join(output_dir, policy_name)
                        category_dir = os.path.join(policy_dir, category_name)
                        os.makedirs(category_dir, exist_ok=True)
                        shutil.move(
                            os.path.join(root, file),
                            os.path.join(category_dir, file)
                        )
    except Exception as e:
        print(f"Error extracting tar file {file_path}: {e}")


def copy_and_extract_files(base_dir, logs_dir):
    """
    Copies and extracts (.tar.gz and .log) files from base_dir to logs_dir.
    It creates a subfolder for each file based on the file name (without extension).
    """
    ite_dirs = []

    for file_name in os.listdir(base_dir):
        if file_name.endswith(('.tar.gz', '.log')):
            ite_name = file_name.split('.')[0]
            ite_dir = os.path.join(logs_dir, ite_name)
            os.makedirs(ite_dir, exist_ok=True)
            ite_dirs.append(ite_dir)
            full_file_name = os.path.join(base_dir, file_name)
            shutil.copy(full_file_name, ite_dir)

            if file_name.endswith('.tar.gz'):
                extract_and_categorize_tar(os.path.join(ite_dir, file_name), ite_dir)

    return ite_dirs


def remove_nested_ite_folders(ite_dirs):
    """
    If the extraction process creates a nested folder with the same name
    as the ITE folder, remove it.
    """
    for ite_dir in ite_dirs:
        nested_ite_dir = os.path.join(ite_dir, os.path.basename(ite_dir))
        if os.path.exists(nested_ite_dir):
            shutil.rmtree(nested_ite_dir)


###############################################################################
# 4. Single-pass log reading (convert to single-line on the fly)
###############################################################################
def read_log_as_single_line(log_file_path):
    """
    Reads a .log file, converts it into a single line (merging lines after the first).
    Returns the single-line string.
    """
    try:
        with open(log_file_path, 'r') as lf:
            lines = [line.strip() for line in lf]
        if len(lines) > 1:
            return ";".join(lines[1:])
        elif lines:
            return lines[0]
        else:
            return ""
    except Exception as e:
        print(f"Error reading log file {log_file_path}: {e}")
        return ""


###############################################################################
# 5. Read logs from ITE->Policy->Category structure (only ALL_APPS_GENERIC)
###############################################################################
def read_logs(logs_dir):
    """
    Recursively searches for .log files in the structure:
      <logs_dir>/<ITE>/<Policy>/<Category>
    BUT we only process the category == 'ALL_APPS_GENERIC', ignoring others.
    This way, we skip unnecessary work for other categories.
    """
    log_data = {}
    total_logs = 0
    unique_policies = set()
    unique_categories = set()

    for ite_dir in os.listdir(logs_dir):
        ite_path = os.path.join(logs_dir, ite_dir)
        if os.path.isdir(ite_path):
            log_data[ite_dir] = {}
            # Check each policy folder
            for policy_name in os.listdir(ite_path):
                policy_path = os.path.join(ite_path, policy_name)
                if os.path.isdir(policy_path):
                    log_data[ite_dir][policy_name] = {}
                    unique_policies.add(policy_name)

                    # We ONLY read logs from the 'ALL_APPS_GENERIC' category
                    category_path = os.path.join(policy_path, 'ALL_APPS_GENERIC')
                    if os.path.isdir(category_path):
                        log_files = {}
                        unique_categories.add('ALL_APPS_GENERIC')

                        for log_file in natsorted(os.listdir(category_path)):
                            if log_file.endswith('.log'):
                                log_file_path = os.path.join(category_path, log_file)
                                single_line = read_log_as_single_line(log_file_path)
                                if single_line:
                                    log_files[log_file] = [single_line]
                                    total_logs += 1
                        # Store logs under 'ALL_APPS_GENERIC'
                        log_data[ite_dir][policy_name]['ALL_APPS_GENERIC'] = log_files

    total_ites = len(log_data)
    total_unique_policies = len(unique_policies)
    total_unique_categories = len(unique_categories)

    print("\n--- Log Reading Finished ---\n")
    print(f"Total ITEs processed: {total_ites}")
    print(f"Unique Policies processed: {total_unique_policies} ({natsorted(unique_policies)})")
    print(f"Unique Categories processed: {total_unique_categories}")
    print(f"Total log files read: {total_logs}")

    return log_data


###############################################################################
# 6. Process files by date
###############################################################################
def process_files_by_date(base_path, results_dir, selected_date, scenario_name='default_config'):
    """
    1) Check and parse 'selected_date'.
    2) Locate base_dir = <base_path>/<selected_date>/<scenario_name>.
    3) Create logs & graph folders under results_dir (non-default scenarios
       get their own results_dir/<scenario_name>/ subtree so evaluating
       several scenarios of one run never mixes their logs/plots).
    4) Copy and extract files from base_dir to logs_dir.
    5) Read logs (only ALL_APPS_GENERIC) and return (log_data, logs_dir, graph_dir).
    """
    try:
        # Validate date format
        datetime.strptime(selected_date, '%d-%m-%Y_%H-%M')
    except ValueError as e:
        print(f"Invalid date format. Please use DD-MM-YYYY_HH-MM. Error: {e}")
        return {}

    # 1) Source directory: <base_path>/<date>/<scenario> ("default_config"
    # etc. -- one folder per simulation.list entry; ReSACO's environment
    # presets add high_mobility/congested_wan/weak_edge alongside it.)
    base_dir = os.path.join(base_path, selected_date, scenario_name)
    if not os.path.isdir(base_dir):
        print(f"Error: Base directory not found: {base_dir}")
        return {}

    # 2) Create logs/graph folders under results_dir. default_config keeps
    # the historical layout (directly under results_dir); other scenarios
    # get a subfolder so nothing collides.
    if scenario_name != 'default_config':
        results_dir = os.path.join(results_dir, scenario_name)
        os.makedirs(results_dir, exist_ok=True)
    logs_dir, graph_dir = create_result_structure(results_dir)

    # 3) Copy and extract files
    ite_dirs = copy_and_extract_files(base_dir, logs_dir)
    remove_nested_ite_folders(ite_dirs)
    remove_progress_folder(logs_dir)

    # 4) Read logs (ALL_APPS_GENERIC only)
    log_data = read_logs(logs_dir)
    return log_data, logs_dir, graph_dir

###############################################################################
# 7. Selection menu (ITE, Policy)
###############################################################################
def print_two_column_menu(labeled_options):
    """Prints a "1. foo  |  2. bar" style two-column menu. `labeled_options`
    is the list of already-numbered strings to display (e.g. "3. EDGE_PRIORITY"),
    shared by select_option's policy/ITE menu and plot_graph's manual
    column picker."""
    max_width = max(len(option) for option in labeled_options) + 2
    format_str = f"{{:<{max_width}}}  |  {{:<{max_width}}}"
    for idx in range(0, len(labeled_options), 2):
        if idx + 1 < len(labeled_options):
            print(format_str.format(labeled_options[idx], labeled_options[idx + 1]))
        else:
            print(labeled_options[idx])


def select_option(options, option_name, auto=False):
    """
    Displays options in two columns, returns a single selected item
    or 'ALL' if the user chooses '0'. With auto=True, skips the prompt
    entirely and returns 'ALL' (matching what choosing '0' would do).
    """
    options = natsorted(options)
    if auto:
        print(f"[--auto] Processing all {option_name.lower()}s")
        return 'ALL'
    print(f"0. Process all {option_name.lower()}s")
    print_two_column_menu([f"{idx + 1}. {opt}" for idx, opt in enumerate(options)])
    while True:
        try:
            selection = int(input(f"Select {option_name} by number: ")) - 1
            if selection == -1:
                return 'ALL'
            elif 0 <= selection < len(options):
                return options[selection]
            else:
                print(f"Invalid {option_name} number. Try again.")
        except ValueError:
            print(f"Please enter a valid number for {option_name}.")


###############################################################################
# 8. Append log lines to DataFrame (via dict)
###############################################################################
def print_log_line(line, data, ite, policy_name, devices, category):
    items = line.split(';')
    data['ite'].append(ite)
    data['policy_name'].append(policy_name)
    data['devices'].append(devices)
    data['category'].append(category)
    index = 0
    for item in items:
        name = all_apps_generic.get(index)
        data[name].append(item)
        index += 1


###############################################################################
# 9. Generate and save plots
###############################################################################
def plot_graph(mean_df, input_date, graph_dir, auto=False):
    """
    If auto=True, automatically plot a predefined list of columns
    with x='devices' and y in auto_plot_columns.
    If auto=False, ask the user to select X and Y columns.
    """
    auto_plot_columns = [
        # ALL
        'num_of_completed_tasks(ALL)',
        'num_of_failed_tasks(ALL)',
        'num_of_uncompleted_tasks(ALL)',
        'num_of_failed_tasks_due_network(ALL)',
        'num_of_failed_tasks_due_vm_capacity(ALL)',
        'num_of_failed_tasks_due_mobility(ALL)',
        'average_service_time(ALL)_(sec)',
        'average_processing_time(ALL)_(sec)',
        'average_network_delay(ALL)_(sec)',
        # CLOUD
        'num_of_completed_tasks(Cloud)',
        'num_of_failed_tasks(Cloud)',
        'num_of_uncompleted_tasks(Cloud)',
        'average_service_time(Cloud)_(sec)',
        'average_processing_time(Cloud)_(sec)',
        'average_server_utilization(Cloud)_(%)',
        'num_of_failed_tasks_due_vm_capacity(Cloud)',
        # EDGE
        'num_of_completed_tasks(Edge)',
        'num_of_failed_tasks(Edge)',
        'num_of_uncompleted_tasks(Edge)',
        'average_service_time(Edge)_(sec)',
        'average_processing_time(Edge)_(sec)',
        'average_server_utilization(Edge)_(%)',
        'num_of_failed_tasks_due_vm_capacity(Edge)',
        # MOBILE
        'num_of_completed_tasks(Mobile)',
        'num_of_failed_tasks(Mobile)',
        'num_of_uncompleted_tasks(Mobile)',
        'average_service_time(Mobile)_(sec)',
        'average_processing_time(Mobile)_(sec)',
        'average_server_utilization(Mobile)_(%)',
        'num_of_failed_tasks_due_vm_capacity(Mobile)'
    ]
    auto_plot_columns.append('num_of_completed_plus_failed_tasks(ALL)')

    if not auto:
        # Manual selection of X and Y
        columns = mean_df.columns.tolist()

        print("\nSelect the columns for the X and Y axis of the graph:\n")
        print_two_column_menu([f"{idx + 1}. {col}" for idx, col in enumerate(columns)])

        while True:
            try:
                print("\n" + "-" * 50)
                x_selection = int(input("\nSelect column for X axis by number: ")) - 1
                y_selection = int(input("Select column for Y axis by number: ")) - 1
                print("\n" + "-" * 50)
                if 0 <= x_selection < len(columns) and 0 <= y_selection < len(columns):
                    x_col = columns[x_selection]
                    y_col = columns[y_selection]
                    break
                else:
                    print("Invalid selection. Please try again.")
            except ValueError:
                print("Please enter a valid number.")
        create_and_save_plot(mean_df, x_col, y_col, input_date, graph_dir)
    else:
        # Automatic mode
        x_col = 'devices'
        for y_col in auto_plot_columns:
            create_and_save_plot(mean_df, x_col, y_col, input_date, graph_dir)


def _convert_to_percentage(policy_df, y_col):
    """Rewrites policy_df[y_col] in place as a percentage of the
    appropriate total (completed+failed tasks, or failed tasks), for the
    handful of count columns that are plotted as percentages rather than
    raw counts. No-op for any other column."""
    if y_col in [
        'num_of_completed_tasks(ALL)',
        'num_of_failed_tasks(ALL)',
        'num_of_uncompleted_tasks(ALL)',
        'num_of_completed_plus_failed_tasks(ALL)'
    ]:
        total = policy_df['num_of_completed_tasks(ALL)'] + policy_df['num_of_failed_tasks(ALL)']
        total[total == 0] = 1
        policy_df[y_col] = (policy_df[y_col] / total) * 100

    elif y_col in [
        'num_of_failed_tasks_due_network(ALL)',
        'num_of_failed_tasks_due_vm_capacity(ALL)',
        'num_of_failed_tasks_due_mobility(ALL)'
    ]:
        total_failed = policy_df['num_of_failed_tasks(ALL)']
        total_failed[total_failed == 0] = 1
        policy_df[y_col] = (policy_df[y_col] / total_failed) * 100


def _folder_for_column(y_col):
    """Which of results/<date>/graph/{ALL,CLOUD,EDGE,MOBILE,OTHERS} a
    given y_col's plot belongs in."""
    if 'ALL' in y_col:
        return 'ALL'
    if 'Cloud' in y_col:
        return 'CLOUD'
    if 'Edge' in y_col:
        return 'EDGE'
    if 'Mobile' in y_col:
        return 'MOBILE'
    return 'OTHERS'


def _save_plot_and_data(x_col, y_col, graph_dir, plotting_data_list):
    output_graph_dir = os.path.join(graph_dir, _folder_for_column(y_col))
    os.makedirs(output_graph_dir, exist_ok=True)

    graph_file_name = os.path.join(output_graph_dir, f"{x_col}_per_{y_col}.png")
    plt.savefig(graph_file_name, bbox_inches='tight', dpi=300)
    plt.close()
    print(f"Graph saved to {graph_file_name}")
    print("\n" + "-" * 50 + "\n")

    if plotting_data_list:
        final_plot_df = pd.concat(plotting_data_list, ignore_index=True)
        final_plot_csv = os.path.join(output_graph_dir, f"{x_col}_per_{y_col}.csv")
        final_plot_df.to_csv(final_plot_csv, index=False)
        print(f"Plot data saved to {final_plot_csv}")
        print("\n" + "-" * 50 + "\n")


def create_and_save_plot(mean_df, x_col, y_col, input_date, graph_dir):
    mean_df[x_col] = mean_df[x_col].astype(int)

    # Plot/legend order is derived from whatever policies are actually
    # present in the data (natural-sorted) rather than a hardcoded list, so
    # this works unchanged for three_tier's policies (ONLY_MOBILE,
    # EDGE_PRIORITY, ...), ReSACO's (RESACO, SAC_BASELINE, ...), or any
    # future application's -- each has a different policy set.
    fixed_policy_order = natsorted(mean_df['policy_name'].unique())

    colors = ['#2b83ba', '#abdda4', '#fdae61', '#d7191c', '#8c564b', '#9467bd',
              '#ff7f0e', '#17becf', '#1f77b4', '#bcbd22']
    markers = ['o', 'x', 's', 'd', '^']

    line_graph_metrics = [
        'average_processing_time(ALL)_(sec)', 'average_service_time(ALL)_(sec)',
        'average_network_delay(ALL)_(sec)', 'average_service_time(Cloud)_(sec)',
        'average_processing_time(Cloud)_(sec)', 'average_service_time(Edge)_(sec)',
        'average_processing_time(Edge)_(sec)', 'average_service_time(Mobile)_(sec)',
        'average_processing_time(Mobile)_(sec)'
    ]

    plt.figure(figsize=(16, 10), dpi=300)
    bar_width = 0.15
    unique_x_values = mean_df[x_col].unique()

    plotting_data_list = []

    if y_col in line_graph_metrics:
        # Line graph
        for i, policy in enumerate(fixed_policy_order):
            if policy in mean_df['policy_name'].unique():
                policy_df = mean_df[mean_df['policy_name'] == policy].copy()
                plotting_data_list.append(policy_df[['policy_name', x_col, y_col]])
                plt.plot(
                    policy_df[x_col], policy_df[y_col],
                    label=policy, color=colors[i % len(colors)],
                    marker=markers[i % len(markers)], markersize=8, linewidth=2
                )
        plt.xticks(unique_x_values, fontsize=18)
    else:
        # Bar graph
        x_positions = np.arange(len(unique_x_values))

        for i, policy in enumerate(fixed_policy_order):
            if policy in mean_df['policy_name'].unique():
                policy_df = mean_df[mean_df['policy_name'] == policy].copy()
                _convert_to_percentage(policy_df, y_col)

                plotting_data_list.append(policy_df[['policy_name', x_col, y_col]])

                if y_col == 'num_of_completed_plus_failed_tasks(ALL)':
                    plt.bar(x_positions + i * bar_width,
                            policy_df[y_col],
                            bar_width,
                            label=policy,
                            color=colors[i % len(colors)],
                            alpha=0.5)
                else:
                    plt.bar(x_positions + i * bar_width,
                            policy_df[y_col],
                            bar_width,
                            label=policy,
                            color=colors[i % len(colors)])

                # Additional bar for completed vs. failed tasks
                if y_col == 'num_of_completed_plus_failed_tasks(ALL)':
                    completed_tasks = policy_df['num_of_completed_tasks(ALL)']
                    total = (policy_df['num_of_completed_tasks(ALL)'] +
                             policy_df['num_of_failed_tasks(ALL)'])
                    total[total == 0] = 1
                    completed_tasks_pct = (completed_tasks / total) * 100
                    plt.bar(x_positions + i * bar_width,
                            completed_tasks_pct,
                            bar_width,
                            color=colors[i % len(colors)],
                            label='_nolegend_')

        plt.xticks(x_positions + bar_width * (len(fixed_policy_order) - 1) / 2,
                   labels=unique_x_values, fontsize=18, ha='center')

    # Format axis labels
    formatted_x_label = format_axis_label(x_col, axis="x")
    formatted_y_label = format_axis_label(y_col, axis="y")

    plt.xlabel(formatted_x_label, labelpad=10, fontsize=20)

    if y_col in ['num_of_completed_tasks(ALL)', 'num_of_completed_plus_failed_tasks(ALL)']:
        plt.ylabel('Task Completion (%)', labelpad=10, fontsize=20)
    elif y_col in [
        'num_of_failed_tasks(ALL)',
        'num_of_failed_tasks_due_network(ALL)',
        'num_of_failed_tasks_due_vm_capacity(ALL)',
        'num_of_failed_tasks_due_mobility(ALL)'
    ]:
        plt.ylabel('Task Failure (%)', labelpad=10, fontsize=20)
    else:
        plt.ylabel(formatted_y_label, labelpad=10, fontsize=20)

    # Set y-axis range for these columns
    if y_col in ['num_of_completed_tasks(ALL)', 'num_of_completed_plus_failed_tasks(ALL)']:
        plt.ylim(82, 101)

    plt.xticks(fontsize=18)
    plt.yticks(fontsize=18)
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.grid(False, axis='x')

    unique_policies = [policy for policy in fixed_policy_order if policy in mean_df['policy_name'].unique()]
    adjust_legend_to_two_rows(unique_policies)
    plt.tight_layout(rect=[0, 0, 1, 0.9])

    _save_plot_and_data(x_col, y_col, graph_dir, plotting_data_list)


def adjust_legend_to_two_rows(unique_policies):
    """
    Dynamically create a two-row legend if necessary,
    distributing policies evenly across columns.
    """
    num_policies = len(unique_policies)
    ncol = (num_policies + 1) // 2
    plt.legend(
        unique_policies,
        loc='upper center',
        bbox_to_anchor=(0.5, -0.15),
        fontsize=18,
        ncol=ncol,
        frameon=True
    )


TIERS = ['ALL', 'Cloud', 'Edge', 'Mobile']
_TIME_METRICS = [('service_time', 'Service Time'), ('processing_time', 'Processing Time')]
_COUNT_METRICS = [('completed_tasks', 'Completed'), ('failed_tasks', 'Failed'), ('uncompleted_tasks', 'Uncompleted')]


def _build_axis_label_mappings():
    """Every (metric, tier) combination follows one of two regular
    patterns; generated here instead of listed by hand so a new metric
    only needs to be added once. ALL-scope timings/counts drop the
    "(All)"/tier qualifier entirely, per-tier timings prefix the tier
    name, and per-tier counts suffix it in parens -- both irregular
    relative to each other, so each needs its own small template. The
    failed-tasks-due-to-* and completed-plus-failed entries don't follow
    either pattern (the underlying log format phrases the ALL-scope and
    per-tier versions differently), so they stay as explicit overrides."""
    mappings = {'devices': 'Number of MDs'}
    for metric_key, metric_label in _TIME_METRICS:
        for tier in TIERS:
            key = f'average_{metric_key}({tier})_(sec)'
            mappings[key] = f'{metric_label} (sec)' if tier == 'ALL' else f'{tier} {metric_label} (sec)'
    mappings['average_network_delay(ALL)_(sec)'] = 'Network Delay (sec)'

    for metric_key, metric_label in _COUNT_METRICS:
        for tier in TIERS:
            key = f'num_of_{metric_key}({tier})'
            mappings[key] = f'{metric_label} Tasks' if tier == 'ALL' else f'{metric_label} Tasks ({tier})'

    mappings.update({
        'num_of_failed_tasks_due_network(ALL)': 'Failed Tasks (Network)',
        'num_of_failed_tasks_due_vm_capacity(ALL)': 'Failed Tasks (VM Capacity)',
        'num_of_failed_tasks_due_mobility(ALL)': 'Failed Tasks (Mobility)',
        'num_of_failed_tasks_due_network(Cloud)': 'Failed Tasks (Network - Cloud)',
        'num_of_failed_tasks_due_vm_capacity(Cloud)': 'Failed Tasks (VM Capacity - Cloud)',
        'num_of_failed_tasks_due_network(Edge)': 'Failed Tasks (Network - Edge)',
        'num_of_failed_tasks_due_vm_capacity(Edge)': 'Failed Tasks (VM Capacity - Edge)',
        'num_of_failed_tasks_due_network(Mobile)': 'Failed Tasks (Network - Mobile)',
        'num_of_failed_tasks_due_vm_capacity(Mobile)': 'Failed Tasks (VM Capacity - Mobile)',
        'num_of_completed_plus_failed_tasks(ALL)': 'Completed+Failed Tasks',
    })
    return mappings


_AXIS_LABEL_MAPPINGS = _build_axis_label_mappings()


def format_axis_label(label, axis="x"):
    return _AXIS_LABEL_MAPPINGS.get(label, label)


###############################################################################
# 10. Main
###############################################################################
def select_ite_and_policy(log_data, auto=False):
    """Prompts for ITE then Policy (each 'ALL' or a single pick), returning
    (selected_ites, ite_part, selected_policies, policy_part) where the
    *_part strings are used as CSV filename fragments. With auto=True,
    both default to 'ALL' without prompting."""
    ite_keys = natsorted(list(log_data.keys()))
    print("\n" + "-" * 50 + "\n")
    print("Available ITEs:")
    ite_selection = select_option(ite_keys, "ITE", auto=auto)

    if ite_selection == 'ALL':
        selected_ites = ite_keys
        ite_part = 'all_ites'
    else:
        selected_ites = [ite_selection]
        ite_part = ite_selection

    policy_keys = natsorted({policy for ite in selected_ites for policy in log_data.get(ite, {}).keys()})
    print("\n" + "-" * 50 + "\n")
    print("Available Policies:")
    policy_selection = select_option(policy_keys, "Policies", auto=auto)

    if policy_selection == 'ALL':
        selected_policies = policy_keys
        policy_part = 'all_policies'
    else:
        selected_policies = [policy_selection]
        policy_part = policy_selection

    return selected_ites, ite_part, selected_policies, policy_part


def build_dataframe(log_data, selected_ites, selected_policies, selected_categories):
    """Flattens the selected ITE/Policy/Category log lines into a single
    pandas DataFrame, one row per (ite, policy, devices) log file."""
    data = {key: [] for key in ['ite', 'policy_name', 'devices', 'category'] + list(all_apps_generic.values())}

    for ite in selected_ites:
        for policy in selected_policies:
            for category in selected_categories:
                # We only have logs under 'ALL_APPS_GENERIC' anyway
                if category in log_data[ite].get(policy, {}):
                    print(f"\n--- Logs for {category} in {policy} of {ite} ---")
                    print(f"{'Log File':<50} {'Devices':<15}")
                    print('-' * 65)

                    for log_file, log_lines in log_data[ite][policy][category].items():
                        devices = [
                            part.replace('DEVICES', '')
                            for part in log_file.split('_') if 'DEVICES' in part
                        ][0]
                        print(f"{log_file:<50} {devices:<15}")
                        print_log_line(log_lines[0], data, ite, policy, devices, category)

    df = pd.DataFrame(data)
    numeric_cols = list(all_apps_generic.values()) + ["devices"]
    df[numeric_cols] = df[numeric_cols].apply(pd.to_numeric, errors='coerce')
    return df


def compute_sorted_and_mean(df):
    """Sorts by (policy, devices) and computes the per-(policy, devices)
    mean across ITEs, adding the completed+failed convenience column."""
    sorted_df = df.sort_values(by=['policy_name', 'devices'])

    numeric_columns = sorted_df.select_dtypes(include=['number']).columns
    mean_df = sorted_df.groupby(['policy_name', 'devices'], as_index=False)[numeric_columns].mean()

    mean_df['num_of_completed_plus_failed_tasks(ALL)'] = \
        mean_df['num_of_completed_tasks(ALL)'] + mean_df['num_of_failed_tasks(ALL)']

    return sorted_df, mean_df


def save_csvs(df, sorted_df, mean_df, logs_dir, input_date, ite_part, policy_part, category_part):
    csv_dir = os.path.join(logs_dir, "csv")
    os.makedirs(csv_dir, exist_ok=True)

    file_raw = os.path.join(csv_dir, f"{input_date}_logs_{ite_part}_{policy_part}_{category_part}.csv")
    df.to_csv(file_raw, index=False)
    print(f"Data saved to {file_raw}")

    file_sorted = os.path.join(csv_dir, f"{input_date}_logs_sorted_{ite_part}_{policy_part}_{category_part}.csv")
    sorted_df.to_csv(file_sorted, index=False)
    print(f"Sorted data saved to {file_sorted}")

    file_mean = os.path.join(csv_dir, f"{input_date}_logs_mean_{ite_part}_{policy_part}_{category_part}.csv")
    mean_df.to_csv(file_mean, index=False)
    print(f"Mean data saved to {file_mean}")


def prompt_and_plot(mean_df, input_date, graph_dir, auto=False):
    print("-" * 50)
    if auto:
        print("[--auto] Plotting automatically")
        plot_graph(mean_df, input_date, graph_dir, auto=True)
        return
    while True:
        plot_choice = input("Do you want to plot graphs automatically or manually? (a/m): ").lower()
        if plot_choice == 'a':
            print("\n--- Automatic Plotting ---\n")
            plot_graph(mean_df, input_date, graph_dir, auto=True)
            break
        elif plot_choice == 'm':
            plot_graph(mean_df, input_date, graph_dir, auto=False)
            break
        else:
            print("Invalid choice. Please enter 'a' for automatic or 'm' for manual.")


def run_app_evaluation(app_dir_name, results_dir, auto=False):
    """Evaluates one EdgeCloudSim application's simulation output
    (scripts/<app_dir_name>/output/<sim_date>/...), saving everything --
    CSVs and plots -- into `results_dir`
    (scripts/evaluation_result/<name>_<YYYYMMDD>/, already created by
    main()). This is the handler behind the ReSACO and three_tier
    EVALUATION_MENU entries; see the comment above EVALUATION_MENU for how
    to register a different kind of evaluation.

    With auto=True, every interactive prompt below is skipped: the latest
    simulation date, all ITEs, all policies, and automatic plotting --
    so this can run unattended in a batch/CI job.
    """
    app_dir = os.path.join(SCRIPT_DIR, app_dir_name)
    base_path = os.path.join(app_dir, "output")

    date_folders = get_available_date_folders(base_path)
    input_date = select_date_folder(date_folders, auto=auto)
    scenario_folders = get_available_scenario_folders(base_path, input_date)
    scenario_name = select_scenario_folder(scenario_folders, auto=auto)
    print("\n" + "-" * 20)
    print(f"Application: {app_dir_name}")
    print(f"Selected simulation date: {input_date}")
    print(f"Selected scenario: {scenario_name}")
    print(f"Results directory: {results_dir}")
    print("-" * 20)

    result = process_files_by_date(base_path, results_dir, input_date, scenario_name)
    if not result:
        sys.exit(1)  # Exit if processing fails
    log_data, logs_dir, graph_dir = result

    # Non-default scenarios carry their name in every CSV/plot filename so
    # artifacts from different scenarios of the same run stay tellable apart.
    if scenario_name != 'default_config':
        input_date = f"{input_date}_{scenario_name}"

    selected_ites, ite_part, selected_policies, policy_part = select_ite_and_policy(log_data, auto=auto)

    # ----------------------------------------------------------------
    # Category selection is forced to ALL_APPS_GENERIC, so we skip
    # interactive picking. We'll just print a reference list (if needed).
    # ----------------------------------------------------------------
    # (We keep the old block commented, as requested.)
    """
    category_keys = natsorted({
        category
        for ite in selected_ites
        for policy in selected_policies
        for category in log_data.get(ite, {}).get(policy, {}).keys()
    })
    print("\n" + "-" * 50 + "\n")
    print("Available Categories:")
    category_selection = select_option(category_keys, "Categories")

    if category_selection == 'ALL':
        selected_categories = category_keys
        category_part = 'all_categories'
    else:
        selected_categories = [category_selection]
        category_part = category_selection
    """
    # Instead, we directly use:
    print("\n--- Forcing Category to: ALL_APPS_GENERIC ---\n")
    selected_categories = ["ALL_APPS_GENERIC"]
    category_part = "ALL_APPS_GENERIC"

    df = build_dataframe(log_data, selected_ites, selected_policies, selected_categories)
    sorted_df, mean_df = compute_sorted_and_mean(df)
    save_csvs(df, sorted_df, mean_df, logs_dir, input_date, ite_part, policy_part, category_part)
    prompt_and_plot(mean_df, input_date, graph_dir, auto=auto)


###############################################################################
# 11. ReSACO's own standalone analysis scripts (convergence / algorithm
#     comparison) -- these live under ReSACO/scripts/, need ReSACO's own
#     venv (they import torch, which this scripts/ environment doesn't
#     have), and are already fully non-interactive on their own. This just
#     runs them through the right interpreter and copies their output
#     artifacts into results_dir so every EVALUATION_MENU option's output
#     lands under scripts/evaluation_result/ in the same place.
###############################################################################
RESACO_DIR = os.path.join(os.path.dirname(SCRIPT_DIR), "ReSACO")


def _resaco_venv_python():
    """Locates ReSACO's own venv interpreter (Windows or Linux/Mac layout),
    not this scripts/ environment's -- ReSACO's scripts need torch."""
    for candidate in (
        os.path.join(RESACO_DIR, "venv", "Scripts", "python.exe"),
        os.path.join(RESACO_DIR, "venv", "bin", "python"),
    ):
        if os.path.exists(candidate):
            return candidate
    return None


def run_resaco_script(script_name, results_dir, auto=False, artifacts=()):
    """Runs `ReSACO/scripts/<script_name>` via ReSACO's own venv, then
    copies each of `artifacts` (paths relative to ReSACO/) into
    results_dir. `auto` is accepted only for a uniform handler signature
    with run_app_evaluation -- both plot_convergence.py and
    compare_algorithms.py are already fully non-interactive, so it has no
    effect here.
    """
    script_path = os.path.join(RESACO_DIR, "scripts", script_name)
    venv_python = _resaco_venv_python()

    if venv_python is None:
        print(f"Error: no ReSACO venv found under {RESACO_DIR}/venv. "
              f"Run `python -m venv venv` and `venv/bin/pip install -r requirements.txt` "
              f"(or venv\\Scripts\\pip on Windows) inside {RESACO_DIR} first.")
        sys.exit(1)
    if not os.path.exists(script_path):
        print(f"Error: {script_path} not found.")
        sys.exit(1)

    print(f"\n--- Running ReSACO/scripts/{script_name} (via {venv_python}) ---\n")
    try:
        subprocess.run([venv_python, script_path], cwd=RESACO_DIR, check=True)
    except subprocess.CalledProcessError as e:
        print(f"Error: {script_name} exited with code {e.returncode}.")
        sys.exit(1)

    copied = []
    for rel_path in artifacts:
        src = os.path.join(RESACO_DIR, rel_path)
        if os.path.exists(src):
            dst = os.path.join(results_dir, os.path.basename(src))
            shutil.copy(src, dst)
            copied.append(dst)
    if copied:
        print(f"\nCopied artifacts to {results_dir}:")
        for path in copied:
            print(f"  {path}")


# Built here (rather than at the top of the file) since each handler needs
# the function it wraps to already exist -- a dict literal's values are
# evaluated immediately, unlike a function body. To add a new evaluation
# option, add one more "N": {"name": ..., "handler": ...} entry; the
# handler just needs to accept (results_dir, auto=False).
EVALUATION_MENU = {
    "1": {"name": "ReSACO", "handler": partial(run_app_evaluation, "ReSACO")},
    "2": {"name": "three_tier", "handler": partial(run_app_evaluation, "three_tier")},
    "3": {"name": "ReSACO_convergence", "handler": partial(
        run_resaco_script, "plot_convergence.py",
        artifacts=("checkpoints/convergence.png", "checkpoints/convergence.csv"),
    )},
    "4": {"name": "ReSACO_compare_algorithms", "handler": partial(
        run_resaco_script, "compare_algorithms.py",
        artifacts=("checkpoints/comparison.csv",),
    )},
}


def parse_args():
    parser = argparse.ArgumentParser(
        description="Evaluate EdgeCloudSim simulation output. Interactive by default.")
    parser.add_argument("choice", nargs="?", default=None,
                         help="Evaluation menu number or name (e.g. '1' or 'ReSACO'). "
                              "Skips the top-level menu prompt if given.")
    parser.add_argument("--auto", action="store_true",
                         help="Skip every interactive prompt (date, ITE, policy, plot mode): "
                              "use the latest date, all ITEs, all policies, and automatic "
                              "plotting -- for batch/CI runs. Requires `choice` to also be "
                              "given, since there's no sensible default for *which* "
                              "application/analysis to run.")
    args = parser.parse_args()
    if args.auto and args.choice is None:
        parser.error("--auto requires a choice argument too, e.g. "
                      "`python evaluate.py ReSACO --auto` (there's no default for "
                      "which application/analysis to run).")
    return args


def main():
    args = parse_args()
    choice_key = prompt_for_evaluation_choice(args.choice)
    entry = EVALUATION_MENU[choice_key]

    today = datetime.now().strftime("%Y%m%d")
    results_dir = os.path.join(SCRIPT_DIR, EVALUATION_RESULT_DIRNAME, f"{entry['name']}_{today}")
    os.makedirs(results_dir, exist_ok=True)

    entry["handler"](results_dir, auto=args.auto)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n--- Exit ---")
