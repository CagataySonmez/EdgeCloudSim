from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Time Complexity Plots ---")

    plot_generic_line(6, 1, 'Simulation Time (minute)', 'ALL_APPS', '', 'upper left', divisor=60)
    plot_generic_line(6, 2, 'Orchestration Algorithm Overhead (μs)', 'ALL_APPS', '', 'upper right', divisor=1000)