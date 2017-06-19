function [] = plotAvgServiceTime()

    plotGenericResult(1, 5, 'Service Time (sec)', 'ALL_APPS', 0);
    plotGenericResult(1, 5, 'Service Time for Augmented Reality App (sec)', 'AUGMENTED_REALITY', 0);
    plotGenericResult(1, 5, 'Service Time for Health App (sec)', 'HEALTH_APP', 0);
    plotGenericResult(1, 5, 'Service Time for Infotainment App (sec)', 'INFOTAINMENT_APP', 0);
    plotGenericResult(1, 5, 'Service Time for Heavy Comp. App (sec)', 'HEAVY_COMP_APP', 0);

    plotGenericResult(2, 5, 'Service Time on Cloudlet (sec)', 'ALL_APPS', 0);
    plotGenericResult(2, 5, {'Service Time on Cloudlet';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', 0);
    plotGenericResult(2, 5, 'Service Time on Cloudlet for Health App (sec)', 'HEALTH_APP', 0);
    plotGenericResult(2, 5, {'Service Time on Cloudlet';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', 0);
    plotGenericResult(2, 5, {'Service Time on Cloudlet';'for Heavy Comp. App (sec)'}, 'HEAVY_COMP_APP', 0);

    plotGenericResult(3, 5, 'Service Time on Cloud (sec)', 'ALL_APPS', 0);
    plotGenericResult(3, 5, {'Service Time on Cloud';'for Augmented Reality App (sec)'}, 'AUGMENTED_REALITY', 0);
    plotGenericResult(3, 5, 'Service Time on Cloud for Health App (sec)', 'HEALTH_APP', 0);
    plotGenericResult(3, 5, {'Service Time on Cloud';'for Infotainment App (sec)'}, 'INFOTAINMENT_APP', 0);
    plotGenericResult(3, 5, {'Service Time on Cloud';'for Heavy Comp. App (sec)'}, 'HEAVY_COMP_APP', 0);

end