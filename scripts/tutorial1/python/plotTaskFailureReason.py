# plotTaskFailureReason.py
from plotGenericLine import plot_generic_line

if __name__ == '__main__':
    print("--- Generating: Task Failure Reason Plots ---")

    # Group 1: VM Capacity
    plot_generic_line(1, 10, 'Failed Task due to VM Capacity (%)', 'ALL_APPS', 'percentage_of_failed', 'upper left')
    plot_generic_line(1, 10, 'Failed Task due to VM Capacity\nfor Augmented Reality App (%)', 'AUGMENTED_REALITY', 'percentage_of_failed', 'upper left')
    plot_generic_line(1, 10, 'Failed Task due to VM Capacity\nfor Health App (%)', 'HEALTH_APP', 'percentage_of_failed', 'upper left')
    plot_generic_line(1, 10, 'Failed Task due to VM Capacity\nfor Infotainment App (%)', 'INFOTAINMENT_APP', 'percentage_of_failed', 'upper left')

    # Group 2: Mobility
    plot_generic_line(1, 11, 'Failed Task due to Mobility (%)', 'ALL_APPS', 'percentage_of_failed', 'upper left')
    plot_generic_line(1, 11, 'Failed Task due to Mobility\nfor Augmented Reality App (%)', 'AUGMENTED_REALITY', 'percentage_of_failed', 'upper left')
    plot_generic_line(1, 11, 'Failed Task due to Mobility\nfor Health App (%)', 'HEALTH_APP', 'percentage_of_failed', 'upper left')
    plot_generic_line(1, 11, 'Failed Task due to Mobility\nfor Infotainment App (%)', 'INFOTAINMENT_APP', 'percentage_of_failed', 'upper left')

    # ... and so on for WLAN, MAN, WAN failures