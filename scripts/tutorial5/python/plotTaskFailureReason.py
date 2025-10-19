from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Task Failure Reason Plots ---")

    plot_generic_line(1, 10, 'Failed Task due to VM Capacity (%)', 'ALL_APPS', 'percentage_of_failed', 'upper left')
    plot_generic_line(1, 11, 'Failed Task due to Mobility (%)', 'ALL_APPS', 'percentage_of_failed', 'upper left')
    plot_generic_line(5, 5, 'Failed Tasks due to WLAN (%)', 'ALL_APPS', 'percentage_of_failed', 'upper left')
