import os
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from scipy.stats import t
import warnings

# Import configuration from the config.py file
from config import get_configuration

# Suppress warnings for cleaner output
warnings.filterwarnings("ignore", category=UserWarning)

def plot_generic_line(row_offset, column_offset, y_label, app_type='ALL_APPS',
                      calculate_percentage=None, legend_pos='best', divisor=1,
                      ignore_zero_values=False):
    """
    Reads simulation data, processes it, and generates a line plot.
    Equivalent to plotGenericLine.m.
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
    
    # Array to store all simulation results
    all_results = np.zeros((num_simulations, len(scenarios), num_device_steps))
    
    # --- Data Reading Loop ---
    for s in range(1, num_simulations + 1):  # Iterations are 1-based
        for i, scenario in enumerate(scenarios):
            for j, num_devices in enumerate(device_counts):
                try:
                    file_name = f'SIMRESULT_DEFAULT_SCENARIO_{scenario}_{num_devices}DEVICES_{app_type}_GENERIC.log'
                    file_path = os.path.join(folder_path, f'ite{s}', file_name)
                    
                    # Read the specific value from the log file
                    data = pd.read_csv(file_path, sep=';', header=None, skiprows=row_offset, nrows=1)
                    value = data.iloc[0, column_offset - 1] # Convert to 0-based index
                    
                    # --- Percentage Calculation ---
                    if calculate_percentage == 'percentage_of_all':
                        total_data = pd.read_csv(file_path, sep=';', header=None, skiprows=1, nrows=1)
                        total_tasks = total_data.iloc[0, 0] + total_data.iloc[0, 1]
                        value = (100 * value) / total_tasks if total_tasks > 0 else 0
                    
                    all_results[s-1, i, j] = value
                except FileNotFoundError:
                    print(f"Warning: File not found -> {file_path}")
                    all_results[s-1, i, j] = np.nan
                except Exception as e:
                    print(f"Error reading {file_path}: {e}")
                    all_results[s-1, i, j] = np.nan

    # --- Data Aggregation ---
    if num_simulations == 1:
        results = np.squeeze(all_results)
    else:
        results = np.nanmean(all_results, axis=0)

    results /= divisor

    # --- Confidence Interval Calculation (95%) ---
    mean_results = np.nanmean(all_results, axis=0) / divisor
    min_ci_vals = np.zeros_like(mean_results)
    max_ci_vals = np.zeros_like(mean_results)

    if num_simulations > 1:
        for i in range(len(scenarios)):
            for j in range(num_device_steps):
                data_slice = all_results[:, i, j][~np.isnan(all_results[:, i, j])] / divisor
                if len(data_slice) > 1:
                    std_err = np.std(data_slice, ddof=1) / np.sqrt(len(data_slice))
                    ci = t.interval(0.95, len(data_slice)-1, loc=mean_results[i, j], scale=std_err)
                    min_ci_vals[i, j] = mean_results[i, j] - ci[0]
                    max_ci_vals[i, j] = ci[1] - mean_results[i, j]

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
        
        if config['plot_confidence_interval']:
            ax.errorbar(device_counts, results[i, :], yerr=[min_ci_vals[i, :], max_ci_vals[i, :]],
                        label=legends[i], color=color, fmt=marker_style, capsize=3)
        else:
            ax.plot(device_counts, results[i, :], marker_style, label=legends[i], color=color)

    ax.set_xlabel(config['x_axis_label'], fontsize=font_sizes[0])
    ax.set_ylabel(y_label, fontsize=font_sizes[0])
    ax.legend(fontsize=font_sizes[1], loc=legend_pos)
    ax.tick_params(axis='both', which='major', labelsize=font_sizes[2])
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