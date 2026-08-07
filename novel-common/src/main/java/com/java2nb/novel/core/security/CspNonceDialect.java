package com.java2nb.novel.core.security;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.model.IProcessableElementTag;

import java.util.Set;

@Component
public final class CspNonceDialect extends AbstractProcessorDialect {

    public static final String NONCE_VARIABLE = "cspNonce";

    public CspNonceDialect() {
        super("Novel CSP nonce", "csp", 1_000);
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        return Set.of(new ScriptNonceProcessor(dialectPrefix));
    }

    private static final class ScriptNonceProcessor extends AbstractElementTagProcessor {

        private ScriptNonceProcessor(String dialectPrefix) {
            super(TemplateMode.HTML, dialectPrefix, "script", false,
                null, false, 1_000);
        }

        @Override
        protected void doProcess(ITemplateContext context, IProcessableElementTag tag,
                                 IElementTagStructureHandler structureHandler) {
            Object value = context.getVariable(NONCE_VARIABLE);
            if (value == null) {
                return;
            }
            String nonce = value.toString();
            if (nonce.matches("[A-Za-z0-9_-]{20,128}")) {
                structureHandler.setAttribute("nonce", nonce);
            }
        }
    }
}
