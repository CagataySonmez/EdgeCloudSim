function [] = plotLocation()
    folderPath = getConfiguration(1);
    numOfSimulations = getConfiguration(3);
    stepOfxAxis = getConfiguration(4);
    startOfMobileDeviceLoop = 500;
    stepOfMobileDeviceLoop = 500;
    endOfMobileDeviceLoop = 1500;
    numOfMobileDevices = (endOfMobileDeviceLoop - startOfMobileDeviceLoop)/stepOfMobileDeviceLoop + 1;
    PlaceCount = 40;

    results = zeros(numOfMobileDevices, PlaceCount);
    
    for s=1:numOfSimulations
        indexCounter = 1;
        for i=startOfMobileDeviceLoop:stepOfMobileDeviceLoop:endOfMobileDeviceLoop
            try
                filePath = strcat(folderPath,'\ite',int2str(s),'\SIMRESULT_ITS_SCENARIO_AI_BASED_',int2str(i),'DEVICES_LOCATION.log');
                readData = dlmread(filePath,';',1,0);

                for j=1:PlaceCount
                    results(indexCounter,j) = results(indexCounter,j) + mean(readData(:,j+1));
                end
            catch err
                error(err)
            end
            indexCounter = indexCounter + 1;
        end
    end
    results = results/numOfSimulations;
    
    xValues = {};
    yValues = {};
    
    indexCounter = 1;
    for i=startOfMobileDeviceLoop:stepOfMobileDeviceLoop:endOfMobileDeviceLoop
        xValues(indexCounter) = {int2str(i)};
        indexCounter = indexCounter + 1;
    end
    for i=1:PlaceCount
        yValues(i) = {int2str(i)}
    end
    
    hFig=figure;
    %pos=getConfiguration(7);
    pos=[10 3 24 6];
    set(hFig, 'Units','centimeters');
    set(hFig, 'Position',pos);
    
    C = [0,172,0;2,171,0;5,171,0;7,171,0;10,171,0;12,171,0;15,171,0;17,171,0;20,171,0;22,171,0;25,170,0;27,170,0;30,170,0;32,170,0;35,170,0;37,170,0;40,170,0;42,170,0;45,170,0;47,169,0;50,169,0;52,169,0;55,169,0;57,169,0;60,169,0;62,169,0;65,169,0;67,169,0;70,168,0;72,168,0;75,168,0;77,168,0;80,168,0;82,168,0;85,168,0;87,168,0;90,168,0;92,168,0;95,167,0;97,167,0;100,167,0;102,167,0;105,167,0;107,167,0;110,167,0;112,167,0;115,167,0;117,166,0;120,166,0;122,166,0;125,166,0;127,166,0;130,166,0;132,166,0;135,166,0;137,166,0;140,165,0;142,165,0;145,165,0;147,165,0;150,165,0;152,165,0;155,165,0;157,165,0;159,164,0;160,164,0;161,163,0;162,162,0;163,162,0;163,161,0;164,160,0;165,159,0;166,159,0;167,158,0;168,157,0;168,157,0;169,156,0;170,155,0;171,154,0;172,154,0;172,153,0;173,152,0;174,152,0;175,151,0;176,150,0;176,149,0;177,149,0;178,148,0;179,147,0;180,147,0;181,146,0;181,145,0;182,144,0;183,144,0;184,143,0;185,142,0;185,142,0;186,141,0;187,140,0;188,139,0;189,139,0;189,138,0;190,137,0;191,137,0;192,136,0;193,135,0;193,134,0;194,134,0;195,133,0;196,132,0;197,132,0;198,131,0;198,130,0;199,129,0;200,129,0;201,128,0;202,127,0;202,127,0;203,126,0;204,125,0;205,124,0;206,124,0;206,123,0;207,122,0;208,122,0;209,121,0;210,120,0;211,119,0;211,119,0;211,118,0;211,117,0;210,116,0;210,115,0;210,114,0;210,113,0;210,112,0;210,111,0;209,110,0;209,109,0;209,108,0;209,107,0;209,106,0;208,105,0;208,104,0;208,103,0;208,102,0;208,102,0;208,101,0;207,100,0;207,99,0;207,98,0;207,97,0;207,96,0;207,95,0;206,94,0;206,93,0;206,92,0;206,91,0;206,90,0;206,89,0;205,88,0;205,87,0;205,86,0;205,85,0;205,84,0;205,84,0;204,83,0;204,82,0;204,81,0;204,80,0;204,79,0;204,78,0;203,77,0;203,76,0;203,75,0;203,74,0;203,73,0;203,72,0;202,71,0;202,70,0;202,69,0;202,68,0;202,67,0;202,67,0;201,66,0;201,65,0;201,64,0;201,63,0;201,62,0;201,61,0;200,60,0;200,59,0;199,58,0;198,57,0;197,56,0;196,55,0;195,54,0;194,53,0;193,53,0;192,52,0;191,51,0;190,50,0;189,49,0;188,48,0;187,47,0;186,46,0;185,45,0;184,44,0;183,43,0;182,42,0;181,41,0;180,41,0;179,40,0;177,39,0;176,38,0;175,37,0;174,36,0;173,35,0;172,34,0;171,33,0;170,32,0;169,31,0;168,30,0;167,29,0;166,29,0;165,28,0;164,27,0;163,26,0;162,25,0;161,24,0;160,23,0;159,22,0;158,21,0;157,20,0;156,19,0;155,18,0;153,18,0;152,17,0;151,16,0;150,15,0;149,14,0;148,13,0;147,12,0;146,11,0;145,10,0;144,9,0;143,8,0;142,7,0;141,6,0;140,6,0;139,5,0;138,4,0;137,3,0;136,2,0;135,1,0;134,0,0];
    
    h = heatmap(yValues, xValues, results, 'Colormap', colormap(C/255),'FontName','Times New Roman','FontSize',11);

    h.Title = 'Mean number of vehicles per places';
    h.XLabel = 'Place IDs';
    h.YLabel = '#Vehicle in simulation';
    
    if(getConfiguration(18) == 1)
        set(hFig, 'PaperUnits', 'centimeters');
        set(hFig, 'PaperPositionMode', 'manual');
        set(hFig, 'PaperPosition',[0 0 pos(3) pos(4)]);
        set(gcf, 'PaperSize', [pos(3) pos(4)]); %Keep the same paper size
        filename = strcat(folderPath,'\position');
        saveas(gcf, filename, 'pdf');
    end
 
end