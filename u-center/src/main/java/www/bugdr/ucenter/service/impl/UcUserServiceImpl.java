package www.bugdr.ucenter.service.impl;

import com.anji.captcha.model.common.RepCodeEnum;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;
import www.bugdr.common.response.R;
import www.bugdr.common.utils.Constants;
import www.bugdr.common.utils.RedisUtils;
import www.bugdr.common.utils.TextUtils;
import www.bugdr.ucenter.base.BaseService;
import www.bugdr.ucenter.pojo.UcToken;
import www.bugdr.ucenter.pojo.UcUser;
import www.bugdr.ucenter.mapper.UcUserMapper;
import www.bugdr.ucenter.pojo.UcUserInfo;
import www.bugdr.ucenter.service.IUcTokenService;
import www.bugdr.ucenter.service.IUcUserInfoService;
import www.bugdr.ucenter.service.IUcUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import www.bugdr.ucenter.utils.ClaimsUtils;
import www.bugdr.ucenter.utils.CookieUtils;
import www.bugdr.ucenter.utils.JwtUtils;
import www.bugdr.ucenter.vo.UserVo;
import www.bugdr.ucenter.vo.LoginVo;
import www.bugdr.ucenter.vo.RegisterVo;

import java.util.Map;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author bugdr
 * @since 2022-01-21
 */
@Slf4j
@Service
public class UcUserServiceImpl extends BaseService<UcUserMapper, UcUser> implements IUcUserService {

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private IUcUserInfoService iUcUserInfoService;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private IUcTokenService iUcTokenService;

    /**
     * 添加用户
     *
     * @param emailCode
     * @param registerVo
     * @return
     */
    @Override
    public R addUser(String emailCode, RegisterVo registerVo) {
        String email = registerVo.getEmail();
        String password = registerVo.getPassword();
        String name = registerVo.getName();
        log.info("registerVo ==> {}", registerVo);
        if (TextUtils.isEmpty(email)) {
            return R.FAILED("注册邮箱不可以为空.");
        }
        if (TextUtils.isEmpty(password)) {
            return R.FAILED("密码不可以为空.");
        }
        if (TextUtils.isEmpty(name)) {
            return R.FAILED("昵称不可以为空.");
        }
        //校验邮箱验证码
        String emailCodeRecord = (String) redisUtils.get(Constants.User.KEY_EMAIL_CODE + email);
        if (TextUtils.isEmpty(emailCodeRecord) || !emailCodeRecord.equals(emailCode)) {
            return R.FAILED("邮箱验证码不正确.");
        }
        //密码长度校验，长度为32位，md5
        if (password.length() != 32) {
            return R.FAILED("请使用MD5加密算法进行装换.");
        }
        //判断邮箱是否有注册
        QueryWrapper<UcUserInfo> emailQueryWrapper = new QueryWrapper<>();
        emailQueryWrapper.eq("email", email);
        emailQueryWrapper.select("id");
        UcUserInfo ucUserInfo = iUcUserInfoService.getBaseMapper().selectOne(emailQueryWrapper);
        if (ucUserInfo != null) {
            return R.FAILED("该邮箱已经被注册.");
        }
        //判断用户名是否有被注册掉
        QueryWrapper<UcUser> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.eq("user_name", name);
        userQueryWrapper.select("id");
        UcUser ucUser = this.baseMapper.selectOne(userQueryWrapper);
        if (ucUser != null) {
            return R.FAILED("该用户名已经被注册.");
        }
        //加密
        String targetPassword = bCryptPasswordEncoder.encode(password);
        //设置头像和状态
        UcUser targetUser = new UcUser();
        targetUser.setAvatar(Constants.User.DEFAULT_AVATAR);
        targetUser.setUserName(name);
        targetUser.setPassword(targetPassword);
        targetUser.setStatus(Constants.User.STATUS_NORMAL);
        targetUser.setSalt(IdWorker.getIdStr());
        //入库
        this.baseMapper.insert(targetUser);
        //添加用户信息表
        UcUserInfo targetUcUserInfo = new UcUserInfo();
        targetUcUserInfo.setUserId(targetUser.getId());
        targetUcUserInfo.setEmail(email);
        iUcUserInfoService.save(targetUcUserInfo);
        //返回结果
        return R.SUCCESS("注册成功.");
    }

    /**
     * 登录
     *
     * @param loginVo
     * @param verifition
     * @return
     */
    @Override
    public R login(LoginVo loginVo, String verifition) {
        log.info("loginVo ==> {}, verifition ==> {} ", loginVo, verifition);
        //校验图灵验证码
        CaptchaVO captchaVO = new CaptchaVO();
        captchaVO.setCaptchaVerification(verifition);
        //这种验证方式会删除，只能验证一次
        ResponseModel response = captchaService.verification(captchaVO);
        String reqCode = response.getRepCode();
        if (!response.isSuccess()) {
            //验证码校验失败，返回信息告诉前端
            //repCode  0000  无异常，代表成功
            //repCode  9999  服务器内部异常
            //repCode  0011  参数不能为空
            //repCode  6110  验证码已失效，请重新获取
            //repCode  6111  验证失败
            //repCode  6112  获取验证码失败,请联系管理员
            if (reqCode != null && reqCode.equals(RepCodeEnum.API_CAPTCHA_COORDINATE_ERROR.getCode())) {
                return R.FAILED(RepCodeEnum.API_CAPTCHA_COORDINATE_ERROR.getDesc());
            } else if (reqCode == null || !reqCode.equals(RepCodeEnum.SUCCESS.getCode())) {
                return R.FAILED("图灵验证码失败.");
            }
        }
        //校验数据
        String name = loginVo.getName();
        if (TextUtils.isEmpty(name)) {
            return R.FAILED("账户不可以为空.");
        }
        String password = loginVo.getPassword();
        if (TextUtils.isEmpty(password)) {
            return R.FAILED("密码不可以为空.");
        }
        if (password.length() != 32) {
            return R.FAILED("请使用MD5加密算法进行装换.");
        }
        //查询用户
        UserVo userByAccount = this.baseMapper.getUserByAccount(name);
        log.info("userByAccount ==> {}", userByAccount);
        if (userByAccount == null) {
            return R.FAILED("账户或者密码不正确.");
        }
        if (Constants.User.STATUS_DISABLE.equals(userByAccount.getStatus())) {
            return R.FAILED("该账户已经被禁用.");
        }
        //对比密码是否正确
        boolean matches = bCryptPasswordEncoder.matches(password, userByAccount.getPassword());
        if (!matches) {
            return R.FAILED("账户或者密码不正确.");
        }
        //创建token
        createToken(userByAccount);
        //返回登录结果
        return R.SUCCESS("登录成功.");
    }

    /**
     * 创建token
     * token （有效两小时，JWT，web，token，存放redis）
     * tokneKey（token的MD5摘要值）
     * refreshToken（有效期1个月，放到数据库）
     * @param userByAccount
     */
    private void createToken(UserVo userByAccount) {

        Map<String, Object> claims = ClaimsUtils.user2Claims(userByAccount);
        //创建token
        String token = JwtUtils.createToken(claims, Constants.Millins.TWO_HOUR, userByAccount.getSalt());
        String tokenKey = DigestUtils.md5DigestAsHex(token.getBytes());
        String refreshToken = JwtUtils.createRefreshToken(userByAccount.getId(), Constants.Millins.MONTH,
                userByAccount.getSalt());
        //去各自的地方
        redisUtils.set(Constants.User.TOKEN_KEY+tokenKey,token,Constants.TimeSecond.TWO_HOUR);
        CookieUtils.setUpCookie(getResponse(),Constants.User.FISHER_KEY,tokenKey);
        UcToken targetRefreshToken = new UcToken();
        targetRefreshToken.setRefreshToken(refreshToken);
        targetRefreshToken.setTokenKey(tokenKey);
        targetRefreshToken.setUserId(userByAccount.getId());
        iUcTokenService.save(targetRefreshToken);
    }
}
