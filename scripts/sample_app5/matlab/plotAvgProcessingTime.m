function [] = plotAvgProcessingTime()

    plotGenericLine(1, 6, 'Average Processing Time (sec)', 'ALL_APPS', 'NorthWest', 0);

    plotGenericLine(2, 6, 'Processing Time on RSU (sec)', 'ALL_APPS', 'NorthWest', 0);

    plotGenericLine(3, 6, 'Processing Time on Cloud (sec)', 'ALL_APPS', 'NorthWest', 0);

end