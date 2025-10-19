from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Average Processing Time Plots ---")

    plot_generic_line(1, 6, 'Processing Time (sec)', 'ALL_APPS', '', 'upper left')
    plot_generic_line(1, 6, 'Processing Time for Danger Assessment App (sec)', 'DANGER_ASSESSMENT', '', 'upper left')
    plot_generic_line(2, 6, 'Processing Time on Edge (sec)', 'ALL_APPS', '', 'upper left')
    plot_generic_line(3, 6, 'Processing Time on Cloud (sec)', 'ALL_APPS', '', 'upper left')