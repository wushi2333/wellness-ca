// Author: Guo Jiali
package sg.edu.nus.wellness.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * Global Chinese/English bilingual support for the web frontend.
 * The active language lives in the HTTP session (WebSession.language). On every
 * request the LanguageInterceptor pushes that choice into LocaleContextHolder,
 * so Thymeleaf #{...} expressions resolve against web-messages_<lang>.properties.
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    @Bean
    public ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("web-messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        source.setFallbackToSystemLocale(false);
        return source;
    }

    /** LocaleResolver is required for Thymeleaf #{...} resolution; the interceptor keeps it in sync. */
    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LanguageInterceptor());
    }

    /** Pushes the session language into LocaleContextHolder + session locale for every web request.
     *  Reads the raw session attribute "WEB_LANGUAGE" (kept in sync by WebSession.saveLanguage). */
    static class LanguageInterceptor implements org.springframework.web.servlet.HandlerInterceptor {
        private static final String LANG_ATTR = "WEB_LANGUAGE";
        @Override
        public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
            HttpSession session = req.getSession(false);
            Object langVal = session != null ? session.getAttribute(LANG_ATTR) : null;
            String lang = langVal == null ? "en" : langVal.toString();
            Locale locale = "zh".equals(lang) ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
            LocaleContextHolder.setLocale(locale);
            if (session != null) {
                session.setAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME, locale);
            }
            return true;
        }
    }
}
