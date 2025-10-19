import os
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from config import get_configuration

def plot_delay_reason(is_edge):
    """
    Generates a stacked bar chart showing the proportion of service time 
    due to processing time versus network delay (WLAN or WAN).
    """
    config = get_configuration()
    
    # Configuration parameters
    folder_path = config['folder_path']
    num_simulations = config['num_iterations']
    start_devices = config['min_devices']
    step_devices = config['step_devices']
    end_devices = config['max_devices']
    
    device_counts = np.arange(start_devices, end_devices + 1, step_devices)
    num_device_steps = len(device_counts)

    all_results = np.zeros((num_simulations, num_device_steps, 2))

    # --- Data Reading ---
    for s in range(1, num_simulations + 1):
        for j, num_devices in enumerate(device_counts):
            try:
                # This plot uses a specific scenario 'WORST_FIT' as in the original script
                file_name = f'SIMRESULT_DEFAULT_SCENARIO_WORST_FIT_{num_devices}DEVICES_ALL_APPS_GENERIC.log'
                file_path = os.path.join(folder_path, f'ite{s}', file_name)

                # Read the entire relevant data block once
                data = pd.read_csv(file_path, sep=';', header=None, skiprows=1, nrows=5)

                if is_edge:
                    # Value1: Processing Time on Edge (row 2, col 6 -> index 1, 5)
                    # Value2: Average WLAN Delay (row 5, col 1 -> index 4, 0)
                    value1 = data.iloc[1, 5]
                    value2 = data.iloc[4, 0]
                else: # Cloud
                    # Value1: Processing Time on Cloud (row 3, col 6 -> index 2, 5)
                    # Value2: Average WAN Delay (row 5, col 3 -> index 4, 2)
                    value1 = data.iloc[2, 5]
                    value2 = data.iloc[4, 2]

                all_results[s-1, j, 0] = value1
                all_results[s-1, j, 1] = value2
            except FileNotFoundError:
                print(f"Warning: File not found -> {file_path}")
                all_results[s-1, j, :] = np.nan
            except Exception as e:
                print(f"Error reading {file_path}: {e}")
                all_results[s-1, j, :] = np.nan

    # Average the results across simulations
    results = np.nanmean(all_results, axis=0)

    # --- Plotting ---
    fig, ax = plt.subplots()
    fig_pos_cm = config['figure_position']
    fig.set_size_inches(fig_pos_cm[2] / 2.54, fig_pos_cm[3] / 2.54)
    plt.rcParams.update({'font.family': 'Times New Roman', 'font.size': 12})

    bar_width = 0.8 # Standard bar width
    
    # Bottom bar (Processing Time)
    ax.bar(device_counts, results[:, 0], width=bar_width, color=[.45, .45, .45], label='processing time')
    # Top bar (Network Delay), starting from the top of the bottom bar
    ax.bar(device_counts, results[:, 1], width=bar_width, bottom=results[:, 0], color=[.90, .90, .90], label='network delay')

    # --- Labels and Legends ---
    if is_edge:
        ax.legend(['processing time', 'WLAN delay'], loc='upper left')
        ax.set_ylabel('Service Time\nProportion on Edge (sec)')
        filename = 'edge_delay_reason'
    else:
        ax.legend(['processing time', 'WAN delay'], loc='upper left')
        ax.set_ylabel('Service Time\nProportion on Cloud (sec)')
        filename = 'cloud_delay_reason'

    ax.set_xlabel(config['x_axis_label'])
    ax.set_xticks(device_counts)
    ax.set_xticklabels(device_counts)
    ax.set_xlim(start_devices - step_devices / 2, end_devices + step_devices / 2)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    
    fig.tight_layout()

    # --- Save Figure ---
    if config['save_figure_as_pdf']:
        output_path = os.path.join(folder_path, f"{filename}.pdf")
        fig.savefig(output_path, bbox_inches='tight')
        print(f"Figure saved to {output_path}")

    plt.show()


if __name__ == '__main__':
    print("--- Generating: Delay Reason Bar Plots ---")
    # Generate the plot for the Edge scenario
    plot_delay_reason(is_edge=True)
    # Generate the plot for the Cloud scenario
    plot_delay_reason(is_edge=False)