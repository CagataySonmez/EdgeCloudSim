from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Average Failed Task Plots ---")
    
    plot_generic_line(1, 2, 'Failed Tasks (%)', 'ALL_APPS', 'percentage_of_all', 'upper left')
    plot_generic_line(1, 2, 'Failed Tasks for\nAugmented Reality App (%)', 'AUGMENTED_REALITY', 'percentage_of_all', 'upper left')
    plot_generic_line(1, 2, 'Failed Tasks for Health App (%)', 'HEALTH_APP', 'percentage_of_all', 'upper left')
    plot_generic_line(1, 2, 'Failed Tasks for\nInfotainment App (%)', 'INFOTAINMENT_APP', 'percentage_of_all', 'upper left')
    plot_generic_line(2, 2, 'Failed Tasks on Edge (%)', 'ALL_APPS', 'percentage_of_all', 'upper left')
    plot_generic_line(2, 2, 'Failed Tasks on Edge\nfor Augmented Reality App (%)', 'AUGMENTED_REALITY', 'percentage_of_all', 'upper left')
    plot_generic_line(2, 2, 'Failed Tasks on Edge for Health App (%)', 'HEALTH_APP', 'percentage_of_all', 'upper left')
    plot_generic_line(2, 2, 'Failed Tasks on Edge for Infotainment App (%)', 'INFOTAINMENT_APP', 'percentage_of_all', 'upper left')
    plot_generic_line(3, 2, 'Failed Tasks on Cloud (%)', 'ALL_APPS', 'percentage_of_all', 'upper left')
    plot_generic_line(3, 2, 'Failed Tasks on Cloud for\nAugmented Reality App (%)', 'AUGMENTED_REALITY', 'percentage_of_all', 'upper left')
    plot_generic_line(3, 2, 'Failed Tasks on Cloud for Health App (%)', 'HEALTH_APP', 'percentage_of_all', 'upper left')
    plot_generic_line(3, 2, 'Failed Tasks on Cloud for Infotainment App (%)', 'INFOTAINMENT_APP', 'percentage_of_all', 'upper left')