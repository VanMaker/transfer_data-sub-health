package com.njau.function;

import com.njau.entity.DropInfo;
import com.njau.entity.SoundInfo;
import com.njau.mapper.DetectionMapper;
import com.njau.utils.ConfigUtils;
import com.njau.utils.ErrorUtils;
import com.njau.utils.MapperUtils;
import org.apache.ibatis.session.SqlSession;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class DetectionThread implements Runnable{
    private ConfigUtils configUtils;
    private String module;
    private String file_path;
    private String time;

    public DetectionThread(ConfigUtils configUtils, String module, String file_path, String time) {
        this.configUtils = configUtils;
        this.module = module;
        this.file_path = file_path;
        this.time = time;
    }

    @Override
    public void run() {
        List<String> resultPaths = null;
        try {
            resultPaths = detect();
        } catch (ErrorUtils e) {
            e.printStackTrace();
        }
        if(null != resultPaths){
            for (String path: resultPaths) {
                System.out.println(path);
            }
            System.out.println( time + "模块【"+ module +"】检测已完毕，检测结果可查询。");
            analysis(resultPaths, module); //在这里面完成数据解析和写进数据库
        }
    }

    private  List<String> detect() throws ErrorUtils {
        String py_parent_path = configUtils.getProperties("py_parent_path");  //实现检测与识别等相关功能模块的python项目路径
        String java_parent_path = configUtils.getProperties("java_parent_path"); //实现数据下载和传输功能的java项目路径
        List<String> resultPaths = new ArrayList<String>();
        String parent_Path = System.getProperty("user.dir").replace(java_parent_path, py_parent_path) + "/";  //parent_Path路径存储检测执行文件所在的父目录
        // python脚本文件路径：/home/yc/Projects/Wens_Detection_System/Wens_Broiler_Detection/run.py
        String pyRunPath =  parent_Path + "run.py";
        // python执行需要的环境路径 ：/home/yc/SOFTWARE/ANACONDA/envs/Wens_Detection_Envs/bin/python
        String envPath = null;
        String inf = null;
        try {
            envPath = configUtils.getProperties("env_python");
            inf = configUtils.getProperties("inf");
        } catch (ErrorUtils e) {
            e.printStackTrace();
        }
        System.out.println(time + "模块【"+ module +"】检测已启动，请耐心等待.....");
        // 调用python可执行文件
        try {
            String[] args=new String[]{envPath, pyRunPath, "--module", module, "--filePath", file_path,  "--inf", inf, "--parentPath", parent_Path}; //filePath为某天待检测的所有文件的父目录，parentPath路径为实现检测的可执行文件的父目录
//            for (String s:
//                    args) {
//                System.out.println(s);
//            }
            ProcessBuilder processBuilder = new ProcessBuilder(args);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains("txt")) {
                    resultPaths.add(line);
                }
            }
            in.close();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultPaths;
    }

    private void analysis(List<String> resultPaths, String module) {
        SqlSession sqlSessionYun = MapperUtils.getSqlSession("yun");
        SqlSession sqlSessionLocal = MapperUtils.getSqlSession("localhost");
        DetectionMapper detectionMapperYun = sqlSessionYun.getMapper(DetectionMapper.class);
        DetectionMapper detectionMapperLocal = sqlSessionLocal.getMapper(DetectionMapper.class);
        for (String resultPath : resultPaths) {
            File result = new File(resultPath);
            try {
                FileInputStream fis = new FileInputStream(result);
                InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                String line;
                if ("voice".equals(module)) {
                    int cough = 0;
                    int chirp = 0;
                    int sum = 0;
                    int score = 0;
                    String health_score = null;
                    String total_score = null;
                    String filename = null;
                    //解析每一行json，封装成一个对象
                    while ((line = br.readLine()) != null) {
                        if (line.contains("{")) {
                            JSONObject jsonObject = new JSONObject(line);
                            filename = jsonObject.getString("name");
                            //每一行的字段进行累加
                            cough = cough + Integer.parseInt(jsonObject.getString("cough"));
                            chirp = chirp + Integer.parseInt(jsonObject.getString("chirp"));
                            sum = sum + Integer.parseInt(jsonObject.getString("sum"));
                        }
                    }
                    String[] strs = filename.split("@");
                    float rate = (float) cough/(float) sum;
                    if(0 <= rate && rate < 0.2) {
                        score = 100;
                    }else if(0.2 <= rate && rate < 0.8) {
                        score = 60;
                    }else {
                        score = 20;
                    }
                    health_score = new DecimalFormat("#.###").format(score * 0.33f);//基础分
                    total_score = new DecimalFormat("#.###").format(score * 0.0838f);
                    Timestamp time = new Timestamp(System.currentTimeMillis());
                    SoundInfo soundInfo = new SoundInfo(strs[0], filename, String.valueOf(cough), String.valueOf(chirp), String.valueOf(sum),String.valueOf(score), health_score, total_score, strs[1], time);
                    detectionMapperYun.insertSoundResult(soundInfo);
                    detectionMapperLocal.insertSoundResult(soundInfo);
                } else if ("drop".equals(module)) {
                    int water = 0;
                    int loose = 0;
                    int blood = 0;
                    int sum = 0;
                    float score = 0;
                    String health_score = null;
                    String total_score = null;
                    String filename = null;
                    //解析每一行json，封装成一个对象
                    while ((line = br.readLine()) != null) {
                        if (line.contains("{")) {
                            JSONObject jsonObject = new JSONObject(line);
                            filename = jsonObject.getString("name");
                            //每一行的字段进行累加
                            water = water + Integer.parseInt(jsonObject.getString("water"));
                            loose = loose + Integer.parseInt(jsonObject.getString("loose"));
                            blood = blood + Integer.parseInt(jsonObject.getString("blood"));
                            sum = sum + Integer.parseInt(jsonObject.getString("sum"));
                        }
                    }
                    String[] strs = filename.split("@");
                    score = 100.0f - (float) sum / 5000.0f * 100.0f;
                    health_score = new DecimalFormat("#.###").format(score * 0.345f);
                    total_score = new DecimalFormat("#.###").format(score * 0.0876f);
                    Timestamp time = new Timestamp(System.currentTimeMillis());
                    DropInfo dropInfo = new DropInfo(strs[0], filename, String.valueOf(water), String.valueOf(loose), String.valueOf(blood), String.valueOf(sum),String.valueOf(score), health_score, total_score, strs[1], time);
                    detectionMapperYun.insertDropResult(dropInfo);
                    detectionMapperLocal.insertDropResult(dropInfo);
                }
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (sqlSessionYun != null) {
            sqlSessionYun.commit();
            sqlSessionYun.close();
        }
        if (sqlSessionLocal != null) {
            sqlSessionLocal.commit();
            sqlSessionLocal.close();
        }
        System.out.println("已更新【"+ module +"】得分");
    }

}
