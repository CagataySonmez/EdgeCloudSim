from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Average Failed Task Plots ---")
    
    plot_generic_line(1, 2, 'Average Failed Tasks (%)', 'ALL_APPS', 'percentage_of_all', 'upper left')