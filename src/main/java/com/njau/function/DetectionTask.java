package com.njau.function;

import com.njau.utils.ConfigUtils;
import com.njau.utils.ErrorUtils;
import com.njau.utils.TimeUtils;

import java.io.File;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;

public class DetectionTask extends TimerTask {
    private ExecutorService executorService;
    private ConfigUtils configUtils;
    private TimeUtils timeUtils;

    public DetectionTask(ExecutorService executorService, ConfigUtils configUtils, TimeUtils timeUtils) {
        this.executorService = executorService;
        this.configUtils = configUtils;
        this.timeUtils = timeUtils;
    }


    @Override
    public void run() {
        String[] modules = null;
        try {
            modules = configUtils.getProperties("modules").split(",");
        } catch (ErrorUtils e) {
            e.printStackTrace();
        }
        for (String module: modules) {
            // 正式版
            String time = timeUtils.get_YMD_HMS_Time()[0];
            // 测试用
//            String time = "2024-4-19";
            String file_path = System.getProperty("user.dir") + "/Detection_Data/" + module + "/" + time + "/";
            if(new File(file_path).exists()) {
                System.out.println("file_path:" + file_path);
                DetectionThread detectionThread = new DetectionThread(configUtils, module, file_path, time);
                executorService.submit(detectionThread);
            }else {
                System.out.println("该文件不存在！");
            }

        }
    }
}
