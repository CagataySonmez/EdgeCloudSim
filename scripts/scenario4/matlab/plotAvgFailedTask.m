function [] = plotAvgFailedTask()

    plotGenericLine(1, 2, 'Failed Tasks (%)', 'ALL_APPS', 'percentage_of_all', 'NorthWest');
    plotGenericLine(1, 2, 'Failed Tasks for Danger Assessment App (%)', 'DANGER_ASSESSMENT', 'percentage_of_all', 'NorthWest');
    plotGenericLine(1, 2, 'Failed Tasks for Navigation App (%)', 'TRAFFIC_MANAGEMENT', 'percentage_of_all', 'NorthWest');
    plotGenericLine(1, 2, {'Failed Tasks for';'Infotainment App (%)'}, 'INFOTAINMENT', 'percentage_of_all', 'NorthWest');

    plotGenericLine(2, 2, 'Failed Tasks on Edge (%)', 'ALL_APPS', 'percentage_of_all', 'NorthWest');
    plotGenericLine(2, 2, {'Failed Tasks on Edge';'for Danger Assessment App (%)'}, 'DANGER_ASSESSMENT', 'percentage_of_all', 'NorthWest');
    plotGenericLine(2, 2, 'Failed Tasks on Edge for Navigation App (%)', 'TRAFFIC_MANAGEMENT', 'percentage_of_all', 'NorthWest');
    plotGenericLine(2, 2, 'Failed Tasks on Edge for Infotainment App (%)', 'INFOTAINMENT', 'percentage_of_all', 'NorthWest');

    plotGenericLine(3, 2, 'Failed Tasks on Cloud (%)', 'ALL_APPS', 'percentage_of_all', 'NorthWest');
    plotGenericLine(3, 2, {'Failed Tasks on Cloud for';'Danger Assessment App (%)'}, 'DANGER_ASSESSMENT', 'percentage_of_all', 'NorthWest');
    plotGenericLine(3, 2, 'Failed Tasks on Cloud for Navigation App (%)', 'TRAFFIC_MANAGEMENT', 'percentage_of_all', 'NorthWest');
    plotGenericLine(3, 2, 'Failed Tasks on Cloud for Infotainment App (%)', 'INFOTAINMENT', 'percentage_of_all', 'NorthWest');
    
end