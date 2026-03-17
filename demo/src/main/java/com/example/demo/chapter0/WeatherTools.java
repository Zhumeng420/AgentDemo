package com.example.demo.chapter0;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WeatherTools {

    // 模拟天气数据库
    private static final Map<String, String> WEATHER_DATA = new ConcurrentHashMap<>();

    static {
        WEATHER_DATA.put("北京", "晴天，温度25℃，风力2级，空气质量良");
        WEATHER_DATA.put("上海", "多云，温度22℃，风力3级，空气质量优");
        WEATHER_DATA.put("广州", "小雨，温度28℃，风力1级，空气质量优");
        WEATHER_DATA.put("深圳", "阵雨，温度27℃，风力2级，空气质量优");
        WEATHER_DATA.put("杭州", "阴天，温度20℃，风力2级，空气质量良");
    }

    @Tool(description = "查询指定城市的实时天气信息")
    public String getWeather(
            @ToolParam(name = "city", description = "城市名称，如'北京'、'上海'") String city) {

        String weather = WEATHER_DATA.get(city);
        if (weather != null) {
            return String.format("%s：%s", city, weather);
        } else {
            return String.format("抱歉，暂未收录%s的天气信息", city);
        }
    }

    @Tool(description = "获取所有支持的城市列表")
    public String getSupportedCities() {
        return String.join("、", WEATHER_DATA.keySet());
    }
}