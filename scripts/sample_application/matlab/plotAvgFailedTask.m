function [] = plotAvgFailedTask()

    plotGenericResult(1, 2, 'Failed Tasks (%)', 'ALL_APPS', 1);
    plotGenericResult(1, 2, {'Failed Tasks for';'Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 1);
    plotGenericResult(1, 2, 'Failed Tasks for Health App (%)', 'HEALTH_APP', 1);
    plotGenericResult(1, 2, 'Failed Tasks for Infotainment App (%)', 'INFOTAINMENT_APP', 1);
    plotGenericResult(1, 2, 'Failed Tasks for Heavy Comp. App (%)', 'HEAVY_COMP_APP', 1);

    plotGenericResult(2, 2, 'Failed Tasks on Cloudlet (%)', 'ALL_APPS', 0);
    plotGenericResult(2, 2, {'Failed Tasks on Cloudlet';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 1);
    plotGenericResult(2, 2, 'Failed Tasks on Cloudlet for Health App (%)', 'HEALTH_APP', 1);
    plotGenericResult(2, 2, 'Failed Tasks on Cloudlet for Infotainment App (%)', 'INFOTAINMENT_APP', 1);
    plotGenericResult(2, 2, 'Failed Tasks on Cloudlet for Heavy Comp. App (%)', 'HEAVY_COMP_APP', 1);
    
    plotGenericResult(3, 2, 'Failed Tasks on Cloud (%)', 'ALL_APPS', 1);
    plotGenericResult(3, 2, {'Failed Tasks on Cloud for';'Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 1);
    plotGenericResult(3, 2, 'Failed Tasks on Cloud for Health App (%)', 'HEALTH_APP', 1);
    plotGenericResult(3, 2, 'Failed Tasks on Cloud for Infotainment App (%)', 'INFOTAINMENT_APP', 1);
    plotGenericResult(3, 2, 'Failed Tasks on Cloud for Heavy Comp. App (%)', 'HEAVY_COMP_APP', 1);
end