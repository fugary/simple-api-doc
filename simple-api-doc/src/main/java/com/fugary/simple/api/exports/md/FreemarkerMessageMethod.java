package com.fugary.simple.api.exports.md;

import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class FreemarkerMessageMethod implements TemplateMethodModelEx {

    @Autowired
    private MessageSource messageSource;

    @Override
    public Object exec(List arguments) throws TemplateModelException {
        if (arguments == null || arguments.isEmpty()) {
            throw new TemplateModelException("Message key is required");
        }
        String key = arguments.get(0).toString();
        Locale locale = LocaleContextHolder.getLocale(); // 默认语言
        if (arguments.size() > 1) {
            locale = Locale.forLanguageTag(arguments.get(1).toString()); // 使用自定义语言
        }
        try {
            return messageSource.getMessage(key, null, locale);
        } catch (NoSuchMessageException e) {
            return key;
        }
    }
}
