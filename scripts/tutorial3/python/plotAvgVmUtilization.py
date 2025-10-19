from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Average VM Utilization Plots ---")
    
    plot_generic_line(2, 8, 'Average VM Utilization of Edge (%)', 'ALL_APPS', '', 'upper left')
    plot_generic_line(3, 8, 'Average VM Utilization of Cloud (%)', 'ALL_APPS', '', 'upper left')