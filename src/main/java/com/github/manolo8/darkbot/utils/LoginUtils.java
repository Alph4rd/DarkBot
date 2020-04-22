package com.github.manolo8.darkbot.utils;

import com.github.manolo8.darkbot.gui.login.LoginForm;
import com.github.manolo8.darkbot.gui.utils.Popups;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginUtils {
    private static final Pattern LOGIN_PATTERN = Pattern.compile("\"bgcdw_login_form\" action=\"(.*)\"");
    private static final Pattern DATA_PATTERN = Pattern.compile("\"src\": \"([^\"]*)\".*}, \\{(.*)}");

    public static LoginData performUserLogin() {
        LoginForm panel = new LoginForm();

        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
        pane.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        Popups.showMessageSync("Login", pane, panel::setDialog);

        LoginData loginData = panel.getResult();
        if (loginData.getPreloaderUrl() == null || loginData.getParams() == null) System.exit(0);
        return loginData;
    }

    public static void usernameLogin(LoginData loginData) {
        String loginUrl = getLoginUrl(HttpUtils.create("https://www.darkorbit.com/").getInputStream());
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);

        HttpUtils.create(loginUrl)
                .setParam("username", loginData.getUsername())
                .setParam("password", loginData.getPassword())
                .getAndCloseInputStream();

        CookieHandler.setDefault(null);

        HttpCookie cookie = cookieManager.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equalsIgnoreCase("dosid"))
                .filter(c -> c.getDomain().matches(".*\\d+.*"))
                .findFirst().orElseThrow(WrongCredentialsException::new);

        loginData.setSid(cookie.getValue(), cookie.getDomain());
    }

    public static void findPreloader(LoginData loginData) {
        InputStream in = HttpUtils.create("https://" + loginData.getUrl() + "/indexInternal.es?action=internalMapRevolution")
                .setFollowRedirects(false)
                .setHeader("Cookie", "dosid=" + loginData.getSid())
                .getInputStream();

        String flashEmbed = new BufferedReader(new InputStreamReader(in))
                .lines()
                .filter(l -> l.contains("flashembed("))
                .findFirst()
                .orElseThrow(WrongCredentialsException::new);

        Matcher m = DATA_PATTERN.matcher(flashEmbed);
        if (m.find()) loginData.setPreloader(m.group(1), m.group(2)
                .replaceAll("\"", "")
                .replaceAll(",", "&")
                .replaceAll(": ", "="));
        else throw new WrongCredentialsException("Can't parse flashembed vars");
    }

    private static String getLoginUrl(InputStream in) {
        return new BufferedReader(new InputStreamReader(in)).lines()
                .map(LOGIN_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1).replace("&amp;", "&"))
                .findFirst().orElseThrow(WrongCredentialsException::new);
    }

    public static class WrongCredentialsException extends IllegalArgumentException {

        public WrongCredentialsException() {
            this("Wrong login data");
        }

        public WrongCredentialsException(String s) {
            super(s);
        }
    }
}
