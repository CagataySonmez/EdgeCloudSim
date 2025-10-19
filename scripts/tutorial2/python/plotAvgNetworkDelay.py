from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Average Network Delay Plots ---")

    plot_generic_line(1, 7, 'Average Network Delay (sec)', 'ALL_APPS', '', 'upper left')
    plot_generic_line(5, 1, 'Average WLAN Delay (sec)', 'ALL_APPS', '', 'upper left')
    plot_generic_line(5, 2, 'Average MAN Delay (sec)', 'ALL_APPS', '', 'upper right')
    plot_generic_line(5, 3, 'Average WAN Delay (sec)', 'ALL_APPS', '', 'upper left')