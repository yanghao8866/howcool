package com.songzhen.howcool.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.ShearCaptcha;
import com.google.common.collect.Maps;
import com.songzhen.howcool.auth.NeedLogin;
import com.songzhen.howcool.biz.UserBizService;
import com.songzhen.howcool.entity.QueryUserEntity;
import com.songzhen.howcool.entity.UserLoginEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 用户相关.
 * ＜p＞用户相关＜br＞
 * 用户相关
 *
 * @author Lucas
 * @date 2018/8/9
 */
@RestController
@RequestMapping("**/v1/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private static final String CAPTCHA_ID_PREFIX = "CAPTCHA_ID";

    @Autowired
    private UserBizService userBizService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 登录
     */
    @PostMapping("login")
    public Map<String, Object> login(@RequestBody UserLoginEntity userLoginEntity) {
        logger.info("login input params userLoginEntity={}", userLoginEntity);
        String userName = userLoginEntity.getUserName();
        String password = userLoginEntity.getPassword();
        String deviceId = userLoginEntity.getDeviceId();
        String captchaId = userLoginEntity.getCaptchaId();
        String captchaCode = userLoginEntity.getCaptchaCode();

        // 存放返回数据
        HashMap<String, Object> retMap = Maps.newHashMap();

        if (StringUtils.isEmpty(userName) || StringUtils.isEmpty(password)) {
            retMap.put("code", "01");
            retMap.put("msg", "用户名和密码不能为空");
            return retMap;
        }

        if (StringUtils.isEmpty(captchaId) || StringUtils.isEmpty(captchaCode)) {
            retMap.put("code", "02");
            retMap.put("msg", "图形验证码ID和图形验证码内容不能为空");
            return retMap;
        }

        String captchaCodeInRedis = redisTemplate.opsForValue().get(captchaId);

        if (StringUtils.isEmpty(captchaCodeInRedis)) {
            retMap.put("code", "03");
            retMap.put("msg", "验证码已过期");
            return retMap;
        }
        //验证图形验证码的有效性
        if (!captchaCodeInRedis.equals(captchaCode)) {
            retMap.put("code", "03");
            retMap.put("msg", "验证码错误");
            return retMap;
        }

        return userBizService.login(userLoginEntity.getUserName(), userLoginEntity.getPassword(), userLoginEntity.getDeviceId());
    }


    /**
     * 登出
     *
     * @param request request
     */
    @NeedLogin
    @PostMapping("logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        Map<String, Object> currentUser = (Map<String, Object>) request.getAttribute("currentUser");

        return userBizService.logout(currentUser.get("uid").toString());
    }

    /**
     * 列表用户
     */
    @NeedLogin
    @PostMapping("pageUser")
    public Map<String, Object> pageUser(@RequestBody QueryUserEntity queryUserEntity) {
        logger.info("login input params queryUserEntity={}", queryUserEntity);
        return userBizService.pageUsers(queryUserEntity);
    }

    /**
     * 生成图形验证码.
     *
     * @date 19:49 2019/4/6
     */
    @GetMapping("getCaptcha")
    public Map<String, Object> getCaptcha(HttpServletRequest request, HttpServletResponse response) {
        logger.info("getCaptcha start ...");
        String captchaId = StringUtils.isEmpty(request.getParameter("captchaId")) ? "" : request.getParameter("captchaId");

        // 存放返回数据
        HashMap<String, Object> retMap = Maps.newHashMap();

        // 查询缓存中captchaId是否存在，不存在则存在风险，请重新获取captchaId
        boolean captchaInRedis = redisTemplate.opsForHash().hasKey(CAPTCHA_ID_PREFIX, captchaId);

        if (!captchaInRedis) {
            retMap.put("code", "03");
            retMap.put("msg", "验证码ID已过期");
            return retMap;
        }

        //定义图形验证码的长、宽、验证码字符数、干扰线宽度
        ShearCaptcha captcha = CaptchaUtil.createShearCaptcha(200, 100, 4, 4);
        //图形验证码写出，可以写出到文件，也可以写出到流
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            captcha.write(outputStream);
        } catch (IOException e) {
            logger.error("createCaptcha have a exception{}", e);
        }

        redisTemplate.opsForValue().set(captchaId, captcha.getCode(), 60, TimeUnit.SECONDS);

        retMap.put("code", "00");
        retMap.put("captchaId", captchaId);

        return retMap;
    }

    /**
     * 生成图形验证码Id.
     *
     * @date 19:49 2019/4/6
     */
    @GetMapping("getCaptchaId")
    public Map<String, Object> getCaptchaId(HttpServletRequest request, HttpServletResponse response) {
        logger.info("getCaptchaId start ...");
        // 存放返回数据
        HashMap<String, Object> retMap = Maps.newHashMap();

        String captchaId = UUID.randomUUID().toString().replace("-", "").toLowerCase();

        redisTemplate.opsForHash().put(CAPTCHA_ID_PREFIX, captchaId, captchaId);
        redisTemplate.expire(CAPTCHA_ID_PREFIX, 30, TimeUnit.DAYS);

        retMap.put("code", "00");
        retMap.put("captchaId", captchaId);

        return retMap;
    }

}
