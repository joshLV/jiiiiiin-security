package cn.jiiiiiin.auth.center.component.authentication;

import cn.jiiiiiin.auth.center.exception.AuthCenterException;
import cn.jiiiiiin.auth.center.exception.AuthCenterUsernameNotFoundException;
import cn.jiiiiiin.mvc.common.utils.MVCUtil;
import cn.jiiiiiin.security.core.authentication.AuthenticationBeanConfig;
import cn.jiiiiiin.user.client.RemoteUserService;
import cn.jiiiiiin.user.vo.CommonUserDetails;
import cn.jiiiiiin.user.vo.Menu;
import cn.jiiiiiin.user.entity.Admin;
import cn.jiiiiiin.user.entity.Interface;
import cn.jiiiiiin.user.entity.Resource;
import cn.jiiiiiin.user.enums.ChannelEnum;
import cn.jiiiiiin.user.enums.ResourceTypeEnum;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.social.security.SocialUserDetails;
import org.springframework.social.security.SocialUserDetailsService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

/**
 * 内管`UserDetailsService`
 * <p>
 * 配置：{@link AuthenticationBeanConfig#userDetailsService()}
 *
 * @author jiiiiiin
 */
@Component
@Slf4j
@AllArgsConstructor
public class AuthCenterUserDetailsService implements UserDetailsService, SocialUserDetailsService {

    private static final String CHANNEL = "channel";
    private final RemoteUserService remoteUserService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("普通登录用户名 {}", username);
        return _getUserDetails(username);
    }

    @Override
    public SocialUserDetails loadUserByUserId(String userId) throws UsernameNotFoundException {
        log.debug("社交登录用户名 {}", userId);
        return _getUserDetails(userId);
    }

    private SocialUserDetails _getUserDetails(String username) {
        Integer channel = Integer.valueOf(MVCUtil.getRequest().getHeader(CHANNEL));
        // TODO 根据channel去获取登录用户的权限信息
        // TODO 改造思路，在前端传过来的`username`参数上拼接渠道标识符
        SocialUserDetails userDetails = null;
        if (channel.equals(ChannelEnum.MNG.getValue())) {
            userDetails = getChannelUserInfo(username);
            // 注：添加新的渠道需要在这里进行配置
        } else {
            throw new AuthCenterException("不支持的渠道请求错误");
        }
        UserDetailsHolder.setContext(userDetails);
        return userDetails;
    }

    private SocialUserDetails getChannelUserInfo(String username) {
        val optionalAdmin = remoteUserService.signInByUsernameOrPhoneNumb(ChannelEnum.MNG, username);
        if (optionalAdmin == null) {
            // 这里不能直接使用`UsernameNotFoundException`
            // 这里有可能是因为`UserServer`服务没有注册导致
            throw new AuthCenterUsernameNotFoundException("用户名密码不符");
        } else {
            val modelMapper = new ModelMapper();
            val admin = modelMapper.map(optionalAdmin, Admin.class);
            _parserResource(admin);
            return new CommonUserDetails(optionalAdmin);
        }
    }

    /**
     * 格式化前端需要的菜单
     *
     * @param optionalAdmin
     */
    private void _parserResource(Admin optionalAdmin) {
        val roles = optionalAdmin.getRoles();
        // 过滤菜单和授权资源
        val menuResources = new HashSet<Resource>();
        // 用户具有的资源
        val authorizeResources = new HashSet<Resource>();
        // 用户具有的接口集合
        val authorizeInterfaces = new HashSet<Interface>();
        roles.forEach(item -> item.getResources().forEach(resource -> {
            authorizeResources.add(resource);
            resource.getInterfaces().forEach(ife -> {
                authorizeInterfaces.add(ife);
            });
            if (resource.getType().equals(ResourceTypeEnum.MENU)) {
                menuResources.add(resource);
            }
        }));

        val menus = new ArrayList<Menu>();
        menuResources.forEach(resource -> {
            // 过滤一级节点
            if (resource.getPid().equals(Resource.IS_ROOT_MENU)) {
                val node = Menu.parserMenu(resource, menuResources);
                menus.add(node);
            }
        });
        menus.sort(Comparator.comparingInt(Menu::getNum));
        optionalAdmin.setAuthorizeResources(authorizeResources);
        optionalAdmin.setAuthorizeInterfaces(authorizeInterfaces);
        optionalAdmin.setMenus(menus);
        log.debug("响应的授权资源 {}", authorizeResources);
        log.debug("响应的授权接口集合 {}", authorizeInterfaces);
        log.debug("响应的菜单 {}", menus);
    }

}
