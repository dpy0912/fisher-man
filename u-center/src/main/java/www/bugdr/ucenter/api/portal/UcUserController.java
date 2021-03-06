package www.bugdr.ucenter.api.portal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import www.bugdr.common.response.R;
import www.bugdr.ucenter.service.IUcUserService;
import www.bugdr.ucenter.vo.LoginVo;
import www.bugdr.ucenter.vo.RegisterVo;

/**
 * <p>
 * 前端控制器
 * </p>
 * <p>
 * 获取图灵验证 captcha (get)
 * 检查当前手机号码是否有被注册 phone_num (get)
 * 获取手机验证码 phone_verify_code （get）
 * 检查当前邮箱是否有被注册 email (get)
 * 获取邮箱验证码 email_verify_code (get)
 * 检查昵称是否有被使用 nickname (get)
 * 提交注册信息 sign_up (post)
 *
 * @author bugdr
 * @since 2022-01-21
 */
@RestController
public class UcUserController {

    @Autowired
    private IUcUserService iUcUserService;

    @PostMapping("/uc/user")
    public R register(@RequestParam("emailCode") String emailCode, @RequestBody RegisterVo registerVo) {
        return iUcUserService.addUser(emailCode, registerVo);
    }

    @PostMapping("/uc/user/login")
    public R login(@RequestBody LoginVo loginVo, @RequestParam("verifition") String verifition) {
        return iUcUserService.login(loginVo, verifition);
    }

    @GetMapping("/uc/user/check/token")
    public R checkToken() {
        return iUcUserService.checkToken();
    }

    @GetMapping("/uc/user/logout")
    public R logout() {
        return iUcUserService.doLogout();
    }
}
