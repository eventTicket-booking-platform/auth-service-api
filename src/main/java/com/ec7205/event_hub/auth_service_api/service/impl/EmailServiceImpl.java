package com.ec7205.event_hub.auth_service_api.service.impl;

import com.ec7205.event_hub.auth_service_api.service.EmailService;
import com.ec7205.event_hub.auth_service_api.utils.EmailTemplateHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

import java.io.IOException;
import java.time.Year;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {
    private final EmailTemplateHelper emailTemplateHelper;
    @Value("${fromEmail}")
    private String senderEmail;
    @Value("${emailKey}")
    private String apiKey;

    @Override
    public boolean sendUserSignupVerificationCode(String toEmail, String subject, String otp,String firstName) throws IOException {
        String htmlBody = emailTemplateHelper.loadHtmlTemplate("templates/otpverification.html");
        htmlBody = htmlBody.replace("${firstName}",firstName);
        htmlBody = htmlBody.replace("${otp}",otp);
        htmlBody = htmlBody.replace("${year}", String.valueOf(Year.now().getValue()));

        Email from = new Email(senderEmail);
        Email to = new Email(toEmail);
        Content content = new Content("text/html",htmlBody);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();

        try{
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
        }catch(IOException e){
            System.out.println(e);
            throw e;
        }
        return true;

    }

    @Override
    public boolean sendHostPassword(String toEmail, String subject, String password, String firstName)
            throws IOException {
        String htmlBody = emailTemplateHelper.loadHtmlTemplate("templates/host-init.html");
        htmlBody = htmlBody.replace("${firstName}", firstName);
        htmlBody = htmlBody.replace("${password}", password);
        htmlBody = htmlBody.replace("${year}", String.valueOf(Year.now().getValue()));

        Email from = new Email(senderEmail);
        Email to = new Email(toEmail);
        Content content = new Content("text/html",htmlBody);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();

        try{
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
        }catch(IOException e){
            System.out.println(e);
            throw e;
        }
        return true;
    }

}
