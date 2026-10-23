import os
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from scipy.stats import t
import warnings

# Import configuration from the config.py file
from config import get_configuration

def plot_generic_line(row_offset, column_offset, y_label, app_type='ALL_APPS',
                      calculate_percentage=None, legend_pos='best', divisor=1,
                      ignore_zero_values=False, hide_lower_values=None,
                      hide_x_axis=None):
    """
    Read simulation data, aggregate repeated runs, and generate a line plot.

    ``hide_x_axis`` may be a two-item sequence containing a one-based scenario
    index and the first device count to hide, matching the MATLAB interface.
    """
    config = get_configuration()
    
    # Extract configuration parameters
    folder_path = config['folder_path']
    num_simulations = config['num_iterations']
    scenarios = config['scenario_types']
    start_devices = config['min_devices']
    step_devices = config['step_devices']
    end_devices = config['max_devices']
    
    device_counts = np.arange(start_devices, end_devices + 1, step_devices)
    num_device_steps = len(device_counts)
    
    if divisor == 0:
        raise ValueError("divisor must be non-zero")

    percentage_denominator_columns = {
        'percentage_of_all': None,
        'percentage_of_completed': 0,
        'percentage_of_failed': 1,
    }
    if calculate_percentage and calculate_percentage not in percentage_denominator_columns:
        raise ValueError(f"Unsupported percentage calculation: {calculate_percentage}")

    all_results = np.full((num_simulations, len(scenarios), num_device_steps), np.nan)
    
    # --- Data Reading Loop ---
    for s in range(1, num_simulations + 1):  # Iterations are 1-based
        for i, scenario in enumerate(scenarios):
            for j, num_devices in enumerate(device_counts):
                try:
                    file_name = f'SIMRESULT_{scenario}_DEFAULT_POLICY_{num_devices}DEVICES_{app_type}_GENERIC.log'
                    file_path = os.path.join(folder_path, f'ite{s}', file_name)
                    
                    # Read the specific value from the log file
                    data = pd.read_csv(file_path, sep=';', header=None, skiprows=row_offset, nrows=1)
                    value = data.iloc[0, column_offset - 1] # Convert to 0-based index
                    
                    # --- Percentage Calculation ---
                    if calculate_percentage:
                        total_data = pd.read_csv(file_path, sep=';', header=None, skiprows=1, nrows=1)
                        denominator_column = percentage_denominator_columns[calculate_percentage]
                        if denominator_column is None:
                            denominator = total_data.iloc[0, 0] + total_data.iloc[0, 1]
                        else:
                            denominator = total_data.iloc[0, denominator_column]
                        value = (100 * value) / denominator if denominator > 0 else 0.0
                    
                    all_results[s-1, i, j] = value
                except FileNotFoundError:
                    print(f"Warning: File not found -> {file_path}")
                    all_results[s-1, i, j] = np.nan
                except Exception as e:
                    print(f"Error reading {file_path}: {e}")
                    all_results[s-1, i, j] = np.nan

    # --- Data Aggregation ---
    aggregation_values = all_results.copy()
    if ignore_zero_values:
        aggregation_values[aggregation_values == 0] = np.nan

    with warnings.catch_warnings():
        warnings.simplefilter("ignore", category=RuntimeWarning)
        results = np.nanmean(aggregation_values, axis=0) / divisor

    if hide_lower_values is not None:
        results[results < hide_lower_values] = np.nan

    if hide_x_axis is not None:
        if len(hide_x_axis) != 2:
            raise ValueError("hide_x_axis must contain a scenario index and device threshold")
        scenario_index, threshold = hide_x_axis
        if not 1 <= scenario_index <= len(scenarios):
            raise ValueError("hide_x_axis scenario index is out of range")
        results[scenario_index - 1, device_counts > threshold] = np.nan

    # --- Confidence Interval Calculation (95%) ---
    min_ci_vals = np.zeros_like(results)
    max_ci_vals = np.zeros_like(results)

    if num_simulations > 1:
        for i in range(len(scenarios)):
            for j in range(num_device_steps):
                data_slice = aggregation_values[:, i, j]
                data_slice = data_slice[~np.isnan(data_slice)] / divisor
                if len(data_slice) > 1:
                    std_err = np.std(data_slice, ddof=1) / np.sqrt(len(data_slice))
                    if std_err > 0:
                        mean_value = np.mean(data_slice)
                        ci = t.interval(0.95, len(data_slice) - 1,
                                        loc=mean_value, scale=std_err)
                        lower_bound = max(0.0, ci[0])
                        min_ci_vals[i, j] = mean_value - lower_bound
                        max_ci_vals[i, j] = max(0.0, ci[1] - mean_value)

    # --- Plotting ---
    fig, ax = plt.subplots()
    fig_pos_cm = config['figure_position']
    fig.set_size_inches(fig_pos_cm[2] / 2.54, fig_pos_cm[3] / 2.54) # Convert cm to inches
    font_sizes = config['font_sizes']
    plt.rcParams.update({'font.family': 'Times New Roman'})
    
    legends = config['legends']
    
    for i in range(len(scenarios)):
        color = config['colors'][i] if config['use_color'] else 'k'
        marker_style = config['color_markers'][i] if config['use_color'] else config['bw_markers'][i]
        marker_style = marker_style.replace('k', '')
        
        if config['plot_confidence_interval']:
            ax.errorbar(device_counts, results[i, :], yerr=[min_ci_vals[i, :], max_ci_vals[i, :]],
                        label=legends[i], color=color, fmt=marker_style, capsize=3)
        else:
            ax.plot(device_counts, results[i, :], marker_style, label=legends[i], color=color)

    ax.set_xlabel(config['x_axis_label'], fontsize=font_sizes[0])
    ax.set_ylabel(y_label, fontsize=font_sizes[0])
    ax.legend(fontsize=font_sizes[1], loc=legend_pos)
    ax.tick_params(axis='both', which='major', labelsize=font_sizes[2])
    tick_interval = max(1, int(config.get('x_tick_interval', 1)))
    ax.set_xticks(device_counts[::tick_interval])
    if config.get('use_scientific_notation_x_axis', False):
        ax.ticklabel_format(axis='x', style='sci', scilimits=(0, 0))
    ax.set_xlim(start_devices - 50, end_devices + 50)
    ax.grid(True, linestyle='--', alpha=0.6)
    fig.tight_layout()

    # --- Save Figure ---
    if config['save_figure_as_pdf']:
        safe_app_type = app_type.replace(' ', '_')
        filename = f"{row_offset}_{column_offset}_{safe_app_type}.pdf"
        output_path = os.path.join(folder_path, filename)
        os.makedirs(folder_path, exist_ok=True)
        fig.savefig(output_path, bbox_inches='tight')
        print(f"Figure saved to {output_path}")

    plt.show()
