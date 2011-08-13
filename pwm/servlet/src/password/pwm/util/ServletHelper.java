/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

package password.pwm.util;

import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.SessionFilter;
import password.pwm.Validator;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Message;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class ServletHelper {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ServletHelper.class);

    /**
     * Wrapper for {@link #forwardToErrorPage(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, javax.servlet.ServletContext, boolean)} )}
     * with forceLogout=true;
     *
     * @param req        Users http request
     * @param resp       Users http response
     * @param theContext The Servlet context
     * @throws java.io.IOException            if there is an error writing to the response
     * @throws javax.servlet.ServletException if there is a problem accessing the http objects
     */
    public static void forwardToErrorPage(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final ServletContext theContext
    )
            throws IOException, ServletException {
        forwardToErrorPage(req, resp, theContext, true);
    }

    /**
     * Forwards the user to the error page.  Callers to this method should populate the sesssion bean's
     * session error state.  If the session error state is null, then this method will populate it
     * with a generic unknown error.
     *
     * @param req         Users http request
     * @param resp        Users http response
     * @param theContext  The Servlet context
     * @param forceLogout if the user should be unauthenticed after showing the error
     * @throws java.io.IOException            if there is an error writing to the response
     * @throws javax.servlet.ServletException if there is a problem accessing the http objects
     */
    public static void forwardToErrorPage(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final ServletContext theContext,
            final boolean forceLogout
    )
            throws IOException, ServletException {
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            final SessionStateBean ssBean = pwmSession.getSessionStateBean();

            if (ssBean.getSessionError() == null) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNKNOWN));
            }

            final String url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_ERROR, req, resp);
            theContext.getRequestDispatcher(url).forward(req, resp);
            if (forceLogout) {
                pwmSession.unauthenticateUser();
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to error page: " + e.toString());
        }
    }

    public static void forwardToLoginPage(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException {
        final String loginServletURL = req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_LOGIN;
        try{
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(loginServletURL, req, resp));
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to error page: " + e.toString());
        }
    }

    public static void forwardToOriginalRequestURL(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException {

        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            final SessionStateBean ssBean = pwmSession.getSessionStateBean();

            String destURL = ssBean.getOriginalRequestURL();

            if (destURL == null || destURL.indexOf(PwmConstants.URL_SERVLET_LOGIN) != -1) { // fallback, shouldnt need to be used.
                destURL = req.getContextPath();
            }

            resp.sendRedirect(SessionFilter.rewriteRedirectURL(destURL, req, resp));
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error forwarding user to original request url: " + e.toString());
        }

    }

    public static void forwardToSuccessPage(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final ServletContext theContext
    )
            throws IOException, ServletException {
        try {
            final SessionStateBean ssBean = PwmSession.getPwmSession(req).getSessionStateBean();

            if (ssBean.getSessionSuccess() == null) {
                ssBean.setSessionSuccess(Message.SUCCESS_UNKNOWN, null);
            }

            final String url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_SUCCESS, req, resp);
            theContext.getRequestDispatcher(url).forward(req, resp);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to success page: " + e.toString());
        }
    }

    public static String debugHttpHeaders(final HttpServletRequest req) {
        final StringBuilder sb = new StringBuilder();

        sb.append("http session headers: ");
        sb.append("\n");

        for (Enumeration enumeration = req.getHeaderNames(); enumeration.hasMoreElements();) {
            final String headerName = (enumeration.nextElement()).toString();
            sb.append(headerName);
            sb.append("=");
            sb.append(req.getHeader(headerName));
            sb.append(enumeration.hasMoreElements() ? "\n" : "");
        }

        return sb.toString();
    }

    public static String debugHttpRequest(final HttpServletRequest req) {
        return debugHttpRequest(req, "");
    }

    public static String debugHttpRequest(final HttpServletRequest req, final String extraText) {
        final StringBuilder sb = new StringBuilder();

        sb.append(req.getMethod());
        sb.append(" request for: ");
        sb.append(req.getRequestURI());

        if (req.getParameterMap().isEmpty()) {
            sb.append(" (no params)");
            if (extraText != null) {
                sb.append(" ");
                sb.append(extraText);
            }
        } else {
            if (extraText != null) {
                sb.append(" ");
                sb.append(extraText);
            }
            sb.append("\n");

            for (final Enumeration paramNameEnum = req.getParameterNames(); paramNameEnum.hasMoreElements();) {
                final String paramName = (String) paramNameEnum.nextElement();
                final Set<String> paramValues = new HashSet<String>();
                try {
                    paramValues.addAll(Validator.readStringsFromRequest(req, paramName, 1024));
                } catch (PwmUnrecoverableException e) {
                    LOGGER.error("unexpected error debugging http request: " + e.toString());
                }

                for (final String paramValue : paramValues) {
                    sb.append("  ").append(paramName).append("=");
                    if (paramName.toLowerCase().contains("password") || paramName.startsWith(PwmConstants.PARAM_RESPONSE_PREFIX)) {
                        sb.append("***removed***");
                    } else {
                        sb.append('\'');
                        sb.append(paramValue);
                        sb.append('\'');
                    }

                    sb.append('\n');
                }
            }

            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Try to find the real path to a file.  Used for configuration, database, and temporary files.
     * <p/>
     * Multiple strategies are used to determine the real path of files because different servlet containers
     * have different symantics.  In principal, servlets are not supposed
     *
     * @param filename       A filename that will be appended to the end of the verified directory
     * @param suggestedPath  The desired path of the file, either relative to the servlet directory or an absolute path
     *                       on the file system
     * @param servletContext The HttpServletContext to be used to retrieve a path.
     * @return a File referencing the desired suggestedPath and filename.
     * @throws Exception if unabble to discover a path.
     */
    public static File figureFilepath(final String filename, final String suggestedPath, final ServletContext servletContext)
            throws Exception {
        final String relativePath = servletContext.getRealPath(suggestedPath);
        return Helper.figureFilepath(filename, suggestedPath, relativePath);
    }

    public static void forwardToWaitPage(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final ServletContext theContext,
            final String nextURL
    )
            throws IOException, ServletException {
        try {
            final SessionStateBean ssBean = PwmSession.getPwmSession(req).getSessionStateBean();
            ssBean.setPostWaitURL(SessionFilter.rewriteURL(nextURL, req, resp));

            final String url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_WAIT, req, resp);
            theContext.getRequestDispatcher(url).forward(req, resp);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to wait page: " + e.toString());
        }

    }

    /*
    public static String readRequestBody(final HttpServletRequest request, final int maxChars) throws IOException {
        final StringBuilder inputData = new StringBuilder();
        String line;
        try {
            final BufferedReader reader = request.getReader();
            while (((line = reader.readLine()) != null) && inputData.length() < maxChars) {
                inputData.append(line);
            }
        } catch (Exception e) {
            LOGGER.error("error reading request body stream: " + e.getMessage());
        }
        return inputData.toString();
    }
    */

    public static String readRequestBody(final HttpServletRequest request, final int maxChars) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = null;
        try {
            final InputStream inputStream = request.getInputStream();
            if (inputStream != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream,"utf8"));
                final char[] charBuffer = new char[1024];
                int bytesRead = -1;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            } else {
                stringBuilder.append("");
            }
        } catch (IOException ex) {
            throw ex;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ex) {
                    throw ex;
                }
            }
        }
        return stringBuilder.toString();
    }
}