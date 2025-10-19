from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Average Processing Time Plots ---")

    # Group 1: Overall Processing Time
    plot_generic_line(1, 6, 'Processing Time (sec)', 'ALL_APPS', '', 'lower right')
    plot_generic_line(1, 6, 'Processing Time for Augmented Reality App (sec)', 'AUGMENTED_REALITY', '', 'lower right')
    plot_generic_line(1, 6, 'Processing Time for Health App (sec)', 'HEALTH_APP', '', 'lower right')
    plot_generic_line(1, 6, 'Processing Time for Infotainment App (sec)', 'INFOTAINMENT_APP', '', 'lower right')

    # Group 2: Processing Time on Edge
    plot_generic_line(2, 6, 'Processing Time on Edge (sec)', 'ALL_APPS', '', 'lower right')
    plot_generic_line(2, 6, 'Processing Time on Edge\nfor Augmented Reality App (sec)', 'AUGMENTED_REALITY', '', 'lower right')
    plot_generic_line(2, 6, 'Processing Time on Edge\nfor Health App (sec)', 'HEALTH_APP', '', 'lower right')
    plot_generic_line(2, 6, 'Processing Time on Edge\nfor Infotainment App (sec)', 'INFOTAINMENT_APP', '', 'lower right')

    # Group 3: Processing Time on Cloud
    plot_generic_line(3, 6, 'Processing Time on Cloud (sec)', 'ALL_APPS', '', 'upper left')
    plot_generic_line(3, 6, 'Processing Time on Cloud\nfor Augmented Reality App (sec)', 'AUGMENTED_REALITY', '', 'upper left')
    plot_generic_line(3, 6, 'Processing Time on Cloud\nfor Health App (sec)', 'HEALTH_APP', '', 'upper left')
    plot_generic_line(3, 6, 'Processing Time on Cloud\nfor Infotainment App (sec)', 'INFOTAINMENT_APP', '', 'upper left')