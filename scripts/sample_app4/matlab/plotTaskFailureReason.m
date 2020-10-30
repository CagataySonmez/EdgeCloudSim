function [] = plotTaskFailureReason()
    plotGenericResult(1, 10, 'Failed Task due to VM Capacity (%)', 'ALL_APPS', 'percentage_for_failed');
%    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_for_failed');
%    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Health App (%)'}, 'HEALTH_APP', 'percentage_for_failed');
%    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'percentage_for_failed');
%    plotGenericResult(1, 10, {'Failed Task due to VM Capacity';'for Heavy Computation App (%)'}, 'HEAVY_COMP_APP', 'percentage_for_failed');
    
    plotGenericResult(1, 11, 'Average Failed Task due to Mobility (%)', 'ALL_APPS', 'percentage_for_failed');
%    plotGenericResult(1, 11, {'Failed Task due to VM Capacity';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_for_failed');
%    plotGenericResult(1, 11, {'Failed Task due to VM Capacity';'for Health App (%)'}, 'HEALTH_APP', 'percentage_for_failed');
%    plotGenericResult(1, 11, {'Failed Task due to VM Capacity';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'percentage_for_failed');
%    plotGenericResult(1, 11, {'Failed Task due to VM Capacity';'for Heavy Computation App (%)'}, 'HEAVY_COMP_APP', 'percentage_for_failed');
    
    plotGenericResult(5, 4, 'Failed Tasks due to WLAN failure (%)', 'ALL_APPS', 'percentage_for_failed');
%    plotGenericResult(5, 4, {'Failed Tasks due to WLAN';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_for_failed');
%    plotGenericResult(5, 4, {'Failed Tasks due to WLAN';'for Health App (%)'}, 'HEALTH_APP', 'percentage_for_failed');
%    plotGenericResult(5, 4, {'Failed Tasks due to WLAN';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'percentage_for_failed');
%    plotGenericResult(5, 4, {'Failed Tasks due to WLAN';'for Heavy Comp. App (%)'}, 'HEAVY_COMP_APP', 'percentage_for_failed');
    
    plotGenericResult(5, 5, 'Failed Tasks due to MAN failure (%)', 'ALL_APPS', 'percentage_for_failed');
%    plotGenericResult(5, 5, {'Failed Tasks due to MAN';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_for_failed');
%    plotGenericResult(5, 5, {'Failed Tasks due to MAN';'for Health App (%)'}, 'HEALTH_APP', 'percentage_for_failed');
%    plotGenericResult(5, 5, {'Failed Tasks due to MAN';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'percentage_for_failed');
%    plotGenericResult(5, 5, {'Failed Tasks due to MAN';'for Heavy Comp. App (%)'}, 'HEAVY_COMP_APP', 'percentage_for_failed');

    plotGenericResult(5, 6, 'Failed Tasks due to WAN failure (%)', 'ALL_APPS', 'percentage_for_failed');
%    plotGenericResult(5, 6, {'Failed Tasks due to WAN';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_for_failed');
%    plotGenericResult(5, 6, {'Failed Tasks due to WAN';'for Health App (%)'}, 'HEALTH_APP', 'percentage_for_failed');
%    plotGenericResult(5, 6, {'Failed Tasks due to WAN';'for Infotainment App (%)'}, 'INFOTAINMENT_APP', 'percentage_for_failed');
%    plotGenericResult(5, 6, {'Failed Tasks due to WAN';'for Heavy Comp. App (%)'}, 'HEAVY_COMP_APP', 'percentage_for_failed');
end
