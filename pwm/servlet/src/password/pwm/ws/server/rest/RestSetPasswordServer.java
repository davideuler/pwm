/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.ws.server.rest;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import password.pwm.ContextManager;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/setpassword")
public class RestSetPasswordServer {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestSetPasswordServer.class);

    @Context
    HttpServletRequest request;
    public static class JsonData {
        public String username;
        public int version;
        //public int strength;
        //public PasswordUtility.PasswordCheckInfo.MATCH_STATUS match;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String doPostSetPassword(
            final @FormParam("username") String username,
            final @FormParam("password") String password
    )
            throws PwmUnrecoverableException
    {
        RestServerHelper.initializeRestRequest(request,"");
        final PwmSession pwmSession = PwmSession.getPwmSession(request);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
        LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(request));

        try {
            final boolean isExternal = RestServerHelper.determineIfRestClientIsExternal(request);
            if (!pwmSession.getSessionStateBean().isAuthenticated()) {
                final ErrorInformation errorInformation = PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo();
                final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation,pwmApplication,pwmSession);
                return restResultBean.toJson();
            }

            try {
                final JsonData jsonData = new JsonData();
                if (username != null && username.length() > 0) {

                    if (!Permission.checkPermission(Permission.HELPDESK, pwmSession, pwmApplication)) {
                        final ErrorInformation errorInformation = PwmError.ERROR_UNAUTHORIZED.toInfo();
                        final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation,pwmApplication,pwmSession);
                        return restResultBean.toJson();
                    }

                    final ChaiUser chaiUser = ChaiFactory.createChaiUser(username, pwmSession.getSessionManager().getChaiProvider());
                    jsonData.username = chaiUser.readCanonicalDN();
                    PasswordUtility.helpdeskSetUserPassword(pwmSession, chaiUser, pwmApplication, password);
                } else {
                    jsonData.username = pwmSession.getUserInfoBean().getUserDN();
                    PasswordUtility.setUserPassword(pwmSession, pwmApplication, password);
                }
                if (isExternal) {
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.REST_SETPASSWORD);
                }
                final RestResultBean restResultBean = new RestResultBean();
                restResultBean.setSuccessMessage(Message.getLocalizedMessage(
                        pwmSession.getSessionStateBean().getLocale(),
                        Message.SUCCESS_PASSWORDCHANGE,
                        pwmApplication.getConfig()));
                restResultBean.setData(jsonData);
                return restResultBean.toJson();
            } catch (PwmOperationalException e) {
                final ErrorInformation errorInformation = e.getErrorInformation();
                final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation,pwmApplication,pwmSession);
                return restResultBean.toJson();
            }

        } catch (Exception e) {
            final String errorMsg = "unexpected error building json response for /setpassword rest service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation,pwmApplication,pwmSession);
            return restResultBean.toJson();
        }
    }
}