from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Average Failed Task Plots ---")
    
    # Group 1: Overall Failed Tasks
    plot_generic_line(row_offset=1, column_offset=2, y_label='Failed Tasks (%)',
                      app_type='ALL_APPS', calculate_percentage='percentage_of_all', legend_pos='upper left')
    plot_generic_line(row_offset=1, column_offset=2, y_label='Failed Tasks for\nAugmented Reality App (%)',
                      app_type='AUGMENTED_REALITY', calculate_percentage='percentage_of_all', legend_pos='upper left')
    plot_generic_line(row_offset=1, column_offset=2, y_label='Failed Tasks for Health App (%)',
                      app_type='HEALTH_APP', calculate_percentage='percentage_of_all', legend_pos='upper left')
    plot_generic_line(row_offset=1, column_offset=2, y_label='Failed Tasks for\nInfotainment App (%)',
                      app_type='INFOTAINMENT_APP', calculate_percentage='percentage_of_all', legend_pos='upper left')

    # Group 2: Failed Tasks on Edge
    plot_generic_line(row_offset=2, column_offset=2, y_label='Failed Tasks on Edge (%)',
                      app_type='ALL_APPS', calculate_percentage='percentage_of_all', legend_pos='upper left')
    plot_generic_line(row_offset=2, column_offset=2, y_label='Failed Tasks on Edge for\nAugmented Reality App (%)',
                      app_type='AUGMENTED_REALITY', calculate_percentage='percentage_of_all', legend_pos='upper left')
    plot_generic_line(row_offset=2, column_offset=2, y_label='Failed Tasks on Edge for Health App (%)',
                      app_type='HEALTH_APP', calculate_percentage='percentage_of_all', legend_pos='upper left')
    plot_generic_line(row_offset=2, column_offset=2, y_label='Failed Tasks on Edge for Infotainment App (%)',
                      app_type='INFOTAINMENT_APP', calculate_percentage='percentage_of_all', legend_pos='upper left')

    # Group 3: Failed Tasks on Cloud
    plot_generic_line(row_offset=3, column_offset=2, y_label='Failed Tasks on Cloud (%)',
                      app_type='ALL_APPS', calculate_percentage='percentage_of_all', legend_pos='upper left')
    plot_generic_line(row_offset=3, column_offset=2, y_label='Failed Tasks on Cloud for\nAugmented Reality App (%)',
                      app_type='AUGMENTED_REALITY', calculate_percentage='percentage_of_all', legend_pos='upper left')
    plot_generic_line(row_offset=3, column_offset=2, y_label='Failed Tasks on Cloud for Health App (%)',
                      app_type='HEALTH_APP', calculate_percentage='percentage_of_all', legend_pos='upper left')
    plot_generic_line(row_offset=3, column_offset=2, y_label='Failed Tasks on Cloud for Infotainment App (%)',
                      app_type='INFOTAINMENT_APP', calculate_percentage='percentage_of_all', legend_pos='upper left')