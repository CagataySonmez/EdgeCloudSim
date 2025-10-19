def get_configuration():
    """
    Returns a dictionary containing all simulation and plotting parameters.
    Based on the final getConfiguration.m file.
    """
    config = {
        'folder_path': '../../../sim_results/tutorial5',
        'num_iterations': 10,
        'x_tick_interval': 1,
        'scenario_types': ['SCENARIO1', 'SCENARIO2', 'SCENARIO3'],
        'legends': ['Case 1', 'Case 2', 'Case 3'],
        'figure_position': [6, 3, 15, 15],  # [left, bottom, width, height] in centimeters
        'font_sizes': [13, 12, 12],  # [xy_label, legend, xy_axis_ticks]
        'x_axis_label': 'Number of Clients',
        'min_devices': 1000,
        'step_devices': 100,
        'max_devices': 2000,
        'use_scientific_notation_x_axis': False, # For future use
        'save_figure_as_pdf': True,
        'plot_confidence_interval': True,
        'use_color': True,
        # Colors for plots
        'colors': [
            [0.55, 0, 0],       # Color for first line
            [0, 0.15, 0.6],     # Color for second line
            [0, 0.23, 0],       # Color for third line
            [0.6, 0, 0.6],      # Color for fourth line
            [0.08, 0.08, 0.08]  # Color for fifth line
        ],
        # Line styles and markers for colorless plots
        'bw_markers': ['-k*', '-ko', '-ks', '-kv', '-kp'],
        # Line styles and markers for colorful plots
        'color_markers': ['-k*', '-ko', '-ks', '-kv', '-kp']
    }
    return config