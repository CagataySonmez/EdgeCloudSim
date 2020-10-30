function [] = plotGenericPie(vmType, appType, threshold)
    folderPath = "D:\git-repos\PhD\EdgeCloudSim\sim_results";
    scenarioType = getConfiguration(5);
    startOfMobileDeviceLoop = 2000;
    stepOfMobileDeviceLoop = 2000;
    endOfMobileDeviceLoop = 2000;
    numOfMobileDevices = (endOfMobileDeviceLoop - startOfMobileDeviceLoop)/stepOfMobileDeviceLoop + 1;
    
    if ~exist('appType','var')
        appType = 1;
    end
    
    if ~exist('vmType','var')
        vmType = 'edge';
    end
    
    total = zeros(1,size(scenarioType,2));
    found = zeros(1,size(scenarioType,2));

    for s=1:size(scenarioType,2)
        for j=1:numOfMobileDevices
            try
                mobileDeviceNumber = startOfMobileDeviceLoop + stepOfMobileDeviceLoop * (j-1);
                filePath = strcat(folderPath,'\ite11\SIMRESULT_ITS_SCENARIO_',char(scenarioType(s)),'_',int2str(mobileDeviceNumber),'DEVICES_SUCCESS.log');

                readData = dlmread(filePath,';',1,0);
                for k=1:size(readData,1)
                    if(readData(k,7) == appType && ((strcmp(vmType,'edge') == 1 && readData(k,3) == 3) || (strcmp(vmType,'cloud') == 1 && readData(k,3) ~= 3) || strcmp(vmType,'all')==1))
                        if(readData(k,12) - readData(k,11) > threshold)
                            found(s) = found(s) + 1;
                        end
                        total(s) = total(s) + 1;
                    end
                end
            catch err
                error(err)
            end
        end
    end
    
    hFig = figure;
    pos=getConfiguration(7);
    fontSizeArray = getConfiguration(8);
    set(hFig, 'Units','centimeters');
    set(hFig, 'Position',pos);
    set(0,'DefaultAxesFontName','Times New Roman');
    set(0,'DefaultTextFontName','Times New Roman');
    set(0,'DefaultAxesFontSize',fontSizeArray(3));

    if(getConfiguration(20) == 1)
        %result = found ./ total .* 100;
        p = pie(found);
        hold on;
    end
    
    txt = reshape(getConfiguration(6),[size(scenarioType,2) 1]);
    pText = findobj(p,'Type','text');
    percentValues = get(pText,'String');
    combinedtxt = strcat(txt,' (',percentValues,')');
    
    for i=1:size(scenarioType,2)
        pText(i).String = combinedtxt(i);
    end
    
    set(pText,'fontsize',fontSizeArray(1))

    hold off;
    axis square
    
    if(getConfiguration(18) == 1)
        set(hFig, 'PaperUnits', 'centimeters');
        set(hFig, 'PaperPositionMode', 'manual');
        set(hFig, 'PaperPosition',[0 0 pos(3) pos(4)]);
        set(gcf, 'PaperSize', [pos(3) pos(4)]); %Keep the same paper size
        filename = strcat(folderPath,'\',int2str(rowOfset),'_',int2str(columnOfset),'_',appType);
        saveas(gcf, filename, 'pdf');
    end
end