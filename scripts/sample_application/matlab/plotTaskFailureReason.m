function [] = plotTaskFailureReason()
    plotGenericResult(1, 10, 'Failed Task due to VM Capacity (%)', 'ALL_APPS', 1);
    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 1);
    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Health App (%)'}, 'HEALTH_APP', 1);
    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 1);
    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Heavy Computation App (%)'}, 'HEAVY_COMP_APP', 1);
    
    plotGenericResult(1, 11, 'Average Failed Task due to Mobility (%)', 'ALL_APPS', 1);
    plotGenericResult(1, 11, {'Failed Task due to VM Capacity';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 1);
    plotGenericResult(1, 11, {'Failed Task due to VM Capacity';'for Health App (%)'}, 'HEALTH_APP', 1);
    plotGenericResult(1, 11, {'Failed Task due to VM Capacity';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 1);
    plotGenericResult(1, 11, {'Failed Task due to VM Capacity';'for Heavy Computation App (%)'}, 'HEAVY_COMP_APP', 1);
    
    plotGenericResult(2, 4, 'Failed Tasks due to WLAN failure (%)', 'ALL_APPS', 1);
    plotGenericResult(2, 4, {'Failed Tasks due to WLAN';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 1);
    plotGenericResult(2, 4, {'Failed Tasks due to WLAN';'for Health App (%)', 'HEALTH_APP'}, 1);
    plotGenericResult(2, 4, {'Failed Tasks due to WLAN';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 1);
    plotGenericResult(2, 4, {'Failed Tasks due to WLAN';'for Heavy Comp. App (%)'}, 'HEAVY_COMP_APP', 1);
    
    plotGenericResult(3, 4, 'Failed Tasks due to WAN failure (%)', 'ALL_APPS', 1);
    plotGenericResult(3, 4, {'Failed Tasks due to WAN';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 1);
    plotGenericResult(3, 4, {'Failed Tasks due to WAN';'for Health App (%)'}, 'HEALTH_APP', 1);
    plotGenericResult(3, 4, {'Failed Tasks due to WAN';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 1);
    plotGenericResult(3, 4, {'Failed Tasks due to WAN';'for Heavy Comp. App (%)'}, 'HEAVY_COMP_APP', 1);
end