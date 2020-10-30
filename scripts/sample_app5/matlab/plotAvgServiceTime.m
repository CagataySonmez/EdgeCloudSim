function [] = plotAvgServiceTime()

     plotGenericLine(1, 5, 'Average Service Time (sec)', 'ALL_APPS', 'NorthWest', 0);
  
     plotGenericLine(2, 5, 'Service Time on RSU (sec)', 'ALL_APPS', 'NorthWest', 0);

     plotGenericLine(3, 5, 'Service Time on Cloud (sec)', 'ALL_APPS', 'NorthWest', 0);

end