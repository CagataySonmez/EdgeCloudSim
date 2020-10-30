function [] = plotAvgQoE()

     plotGenericLine(1, 13, 'Average QoE (%)', 'ALL_APPS', 'SouthWest', 0);
     plotGenericLine(1, 13, 'QoE for Danger Assessment App (%)', 'DANGER_ASSESSMENT', 'SouthWest', 0);
     plotGenericLine(1, 13, 'QoE for Navigation App (%)', 'TRAFFIC_MANAGEMENT', 'SouthWest', 0);
     plotGenericLine(1, 13, 'QoE for Infotainment App (%)', 'INFOTAINMENT', 'SouthWest', 0);

end