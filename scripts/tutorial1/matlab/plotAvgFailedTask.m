function [] = plotAvgFailedTask()

    plotGenericLine(1, 2, 'Failed Tasks (%)', 'ALL_APPS', 'percentage_of_all', 'NorthWest');
    plotGenericLine(1, 2, {'Failed Tasks for';'Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_of_all', 'NorthWest');
    plotGenericLine(1, 2, 'Failed Tasks for Health App (%)', 'HEALTH_APP', 'percentage_of_all', 'NorthWest');
    plotGenericLine(1, 2, {'Failed Tasks for';'Infotainment App (%)'}, 'INFOTAINMENT_APP', 'percentage_of_all', 'NorthWest');

    plotGenericLine(2, 2, 'Failed Tasks on Edge (%)', 'ALL_APPS', 'percentage_of_all', 'NorthWest');
    plotGenericLine(2, 2, {'Failed Tasks on Edge';'for Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_of_all', 'NorthWest');
    plotGenericLine(2, 2, 'Failed Tasks on Edge for Health App (%)', 'HEALTH_APP', 'percentage_of_all', 'NorthWest');
    plotGenericLine(2, 2, 'Failed Tasks on Edge for Infotainment App (%)', 'INFOTAINMENT_APP', 'percentage_of_all', 'NorthWest');

    plotGenericLine(3, 2, 'Failed Tasks on Cloud (%)', 'ALL_APPS', 'percentage_of_all', 'NorthWest');
    plotGenericLine(3, 2, {'Failed Tasks on Cloud for';'Augmented Reality App (%)'}, 'AUGMENTED_REALITY', 'percentage_of_all', 'NorthWest');
    plotGenericLine(3, 2, 'Failed Tasks on Cloud for Health App (%)', 'HEALTH_APP', 'percentage_of_all', 'NorthWest');
    plotGenericLine(3, 2, 'Failed Tasks on Cloud for Infotainment App (%)', 'INFOTAINMENT_APP', 'percentage_of_all', 'NorthWest');
    
end