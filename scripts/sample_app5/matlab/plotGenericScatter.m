function [] = plotGenericScatter(yLabel, xLabel, legendPos, vmType, appType, drawLine)
    folderPath = "D:\git-repos\PhD\EdgeCloudSim\sim_results";
    scenarioType = getConfiguration(5);
    simulationTime = getConfiguration(2);
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
    
    result_x = NaN(size(scenarioType,2), 5000);
    result_y = NaN(size(result_x,1), size(result_x,2));
    %result_sz = NaN(size(result_x,1), size(result_x,2));
    
    for s=1:size(scenarioType,2)
        index = 1;
        firstDeviceId = -1;
        for j=1:numOfMobileDevices
            try
                mobileDeviceNumber = startOfMobileDeviceLoop + stepOfMobileDeviceLoop * (j-1);
                filePath = strcat(folderPath,'\ite11\SIMRESULT_ITS_SCENARIO_',char(scenarioType(s)),'_',int2str(mobileDeviceNumber),'DEVICES_SUCCESS.log');

                readData = dlmread(filePath,';',1,0);
                for k=1:size(readData,1)
                    if(readData(k,7) == appType && ((strcmp(vmType,'edge') == 1 && readData(k,3) == 3) || (strcmp(vmType,'cloud') == 1 && readData(k,3) ~= 3) || strcmp(vmType,'all')==1))
                        if(firstDeviceId == -1)
                            firstDeviceId = readData(k,2);
                        end 
                        if(readData(k,2) == firstDeviceId) % && readData(k,11) - readData(k,10) < 2
                            result_y(s, index) = readData(k,12) - readData(k,11);
                            result_x(s, index) = readData(k,12) / 60;
                            %result_sz(s, index) = j*10;
                            index = index + 1;
                        end
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
        for i=1:size(scenarioType,2)
            scatter(result_x(i,:), result_y(i,:));
            hold on;
        end
        
        if exist('drawLine','var')
            y = [drawLine, drawLine];
            x = [0, simulationTime];
            plot(x,y);
        end
    end
       
    legends = getConfiguration(6);
    lgnd = legend(legends,'Location',legendPos);
    %lgnd.Position=[0.21,0.8,0.2,0.01];
    if(getConfiguration(20) == 1)
        set(lgnd,'color','none');
    end

    hold off;
    axis square
    xlabel(xLabel);
    ylabel(yLabel);

    set(get(gca,'Xlabel'),'FontSize',fontSizeArray(1));
    set(get(gca,'Ylabel'),'FontSize',fontSizeArray(1));
    set(lgnd,'FontSize',fontSizeArray(2));
    
    if(getConfiguration(18) == 1)
        set(hFig, 'PaperUnits', 'centimeters');
        set(hFig, 'PaperPositionMode', 'manual');
        set(hFig, 'PaperPosition',[0 0 pos(3) pos(4)]);
        set(gcf, 'PaperSize', [pos(3) pos(4)]); %Keep the same paper size
        filename = strcat(folderPath,'\',int2str(rowOfset),'_',int2str(columnOfset),'_',appType);
        saveas(gcf, filename, 'pdf');
    end
end