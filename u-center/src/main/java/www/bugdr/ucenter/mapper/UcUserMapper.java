package www.bugdr.ucenter.mapper;

import www.bugdr.ucenter.pojo.UcUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import www.bugdr.ucenter.vo.LoginBean;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author bugdr
 * @since 2022-01-21
 */
public interface UcUserMapper extends BaseMapper<UcUser> {

    UcUser getUserByAccount(String name);
}
