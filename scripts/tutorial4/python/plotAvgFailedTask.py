from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Average Failed Task Plots ---")
    
    plot_generic_line(1, 2, 'Failed Tasks (%)', 'ALL_APPS', 'percentage_of_all', 'upper left')
    plot_generic_line(1, 2, 'Failed Tasks for Danger Assessment App (%)', 'DANGER_ASSESSMENT', 'percentage_of_all', 'upper left')
    plot_generic_line(1, 2, 'Failed Tasks for Navigation App (%)', 'TRAFFIC_MANAGEMENT', 'percentage_of_all', 'upper left')
    plot_generic_line(1, 2, 'Failed Tasks for\nInfotainment App (%)', 'INFOTAINMENT', 'percentage_of_all', 'upper left')
    plot_generic_line(2, 2, 'Failed Tasks on Edge (%)', 'ALL_APPS', 'percentage_of_all', 'upper left')
    plot_generic_line(2, 2, 'Failed Tasks on Edge\nfor Danger Assessment App (%)', 'DANGER_ASSESSMENT', 'percentage_of_all', 'upper left')
    plot_generic_line(2, 2, 'Failed Tasks on Edge for Navigation App (%)', 'TRAFFIC_MANAGEMENT', 'percentage_of_all', 'upper left')
    plot_generic_line(2, 2, 'Failed Tasks on Edge for Infotainment App (%)', 'INFOTAINMENT', 'percentage_of_all', 'upper left')
    plot_generic_line(3, 2, 'Failed Tasks on Cloud (%)', 'ALL_APPS', 'percentage_of_all', 'upper left')
    plot_generic_line(3, 2, 'Failed Tasks on Cloud for\nDanger Assessment App (%)', 'DANGER_ASSESSMENT', 'percentage_of_all', 'upper left')
    plot_generic_line(3, 2, 'Failed Tasks on Cloud for Navigation App (%)', 'TRAFFIC_MANAGEMENT', 'percentage_of_all', 'upper left')
    plot_generic_line(3, 2, 'Failed Tasks on Cloud for Infotainment App (%)', 'INFOTAINMENT', 'percentage_of_all', 'upper left')