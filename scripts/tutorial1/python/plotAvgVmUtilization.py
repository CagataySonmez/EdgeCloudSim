from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Average VM Utilization Plots ---")

    plot_generic_line(2, 8, 'Average VM Utilization (%)', 'ALL_APPS', '', 'upper left')
    plot_generic_line(2, 8, 'Average VM Utilization\nfor Augmented Reality App (%)', 'AUGMENTED_REALITY', '', 'upper left')
    plot_generic_line(2, 8, 'Average VM Utilization for Health App (%)', 'HEALTH_APP', '', 'upper left')
    plot_generic_line(2, 8, 'Average VM Utilization for Infotainment App (%)', 'INFOTAINMENT_APP', '', 'upper left')
