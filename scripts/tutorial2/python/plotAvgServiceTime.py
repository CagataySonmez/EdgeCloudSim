from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Average Service Time Plots ---")

    plot_generic_line(1, 5, 'Service Time (sec)', 'ALL_APPS', '', 'upper left')
    plot_generic_line(2, 5, 'Service Time on Edge (sec)', 'ALL_APPS', '', 'upper left')
    plot_generic_line(3, 5, 'Service Time on Cloud (sec)', 'ALL_APPS', '', 'upper left')