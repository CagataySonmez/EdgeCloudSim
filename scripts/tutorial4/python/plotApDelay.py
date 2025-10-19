import os
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from config import get_configuration

if __name__ == '__main__':
    print("--- Generating: AP Delay Plot ---")
    config = get_configuration()
    folder_path = config['folder_path']
    num_simulations = config['num_iterations']
    start_devices, step_devices, end_devices = config['min_devices'], config['step_devices'], config['max_devices']
    device_counts = np.arange(start_devices, end_devices + 1, step_devices)
    place_types = ['AP 1 (60 km/h)', 'AP 4 (40 km/h)', 'AP 6 (20 km/h)']
    
    results = np.full((num_simulations, len(place_types), len(device_counts)), np.nan)

    for s in range(1, num_simulations + 1):
        for j, num_devices in enumerate(device_counts):
            try:
                f1 = f'SIMRESULT_TRAFFIC_HEURISTIC_DEFAULT_POLICY_{num_devices}DEVICES_AP_UPLOAD_DELAY.log'
                f2 = f'SIMRESULT_TRAFFIC_HEURISTIC_DEFAULT_POLICY_{num_devices}DEVICES_AP_DOWNLOAD_DELAY.log'
                
                path1 = os.path.join(folder_path, f'ite{s}', f1)
                path2 = os.path.join(folder_path, f'ite{s}', f2)

                data1 = pd.read_csv(path1, sep=';', header=None, skiprows=60)
                data2 = pd.read_csv(path2, sep=';', header=None, skiprows=60)
                
                for i in range(len(place_types)):
                    mean_delay = data1.iloc[:, i + 1].mean() + data2.iloc[:, i + 1].mean()
                    results[s, i, j] = mean_delay
            except Exception:
                continue

    avg_results = np.nanmean(results, axis=0)

    fig, ax = plt.subplots()
    fig_pos_cm = config['figure_position']
    fig.set_size_inches(fig_pos_cm[2] / 2.54, fig_pos_cm[3] / 2.54)
    font_sizes = config['font_sizes']
    plt.rcParams.update({'font.family': 'Times New Roman'})

    markers = [':k*', ':ko', ':ks']
    for i in range(len(place_types)):
        ax.plot(device_counts, avg_results[i, :], markers[i], 
                label=place_types[i], 
                color=config['colors'][i], 
                markerfacecolor=config['colors'][i],
                linewidth=1.5)

    ax.set_xlabel(config['x_axis_label'], fontsize=font_sizes[0])
    ax.set_ylabel('Average Network Delay (sec)', fontsize=font_sizes[0])
    ax.legend(fontsize=font_sizes[1], loc='upper left')
    ax.tick_params(axis='both', which='major', labelsize=font_sizes[2])
    ax.set_xlim(start_devices - 50, end_devices + 50)
    ax.grid(True, linestyle='--', alpha=0.6)
    fig.tight_layout()

    if config['save_figure_as_pdf']:
        output_path = os.path.join(folder_path, "apDelay.pdf")
        fig.savefig(output_path, bbox_inches='tight')
        print(f"Figure saved to {output_path}")

    plt.show()