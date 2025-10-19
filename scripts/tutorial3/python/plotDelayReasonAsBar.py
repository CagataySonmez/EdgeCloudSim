import os
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from config import get_configuration

def plot_delay_reason(is_edge):
    config = get_configuration()
    folder_path = config['folder_path']
    num_simulations = config['num_iterations']
    start_devices, step_devices, end_devices = config['min_devices'], config['step_devices'], config['max_devices']
    device_counts = np.arange(start_devices, end_devices + 1, step_devices)
    all_results = np.zeros((num_simulations, len(device_counts), 2))

    for s in range(1, num_simulations + 1):
        for j, num_devices in enumerate(device_counts):
            try:
                file_name = f'SIMRESULT_DEFAULT_SCENARIO_RANDOM_FIT_{num_devices}DEVICES_ALL_APPS_GENERIC.log'
                file_path = os.path.join(folder_path, f'ite{s}', file_name)
                data = pd.read_csv(file_path, sep=';', header=None, skiprows=1, nrows=5)
                value1 = data.iloc[1, 5] if is_edge else data.iloc[2, 5]
                value2 = data.iloc[4, 0] if is_edge else data.iloc[4, 2]
                all_results[s-1, j, :] = [value1, value2]
            except Exception:
                all_results[s-1, j, :] = np.nan

    results = np.nanmean(all_results, axis=0)

    fig, ax = plt.subplots()
    fig.set_size_inches(config['figure_position'][2] / 2.54, config['figure_position'][3] / 2.54)
    plt.rcParams.update({'font.family': 'Times New Roman', 'font.size': 12})

    ax.bar(device_counts, results[:, 0], width=step_devices*0.8, color=[.45, .45, .45], label='processing time')
    ax.bar(device_counts, results[:, 1], width=step_devices*0.8, bottom=results[:, 0], color=[.90, .90, .90], label='network delay')

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
    fig.tight_layout()

    if config['save_figure_as_pdf']:
        output_path = os.path.join(folder_path, f"{filename}.pdf")
        fig.savefig(output_path, bbox_inches='tight')
        print(f"Figure saved to {output_path}")

    plt.show()

if __name__ == '__main__':
    print("--- Generating: Delay Reason Bar Plots ---")
    plot_delay_reason(is_edge=True)
    plot_delay_reason(is_edge=False)