/**
 *
 */
package com.fugary.simple.api.utils.servlet;

import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.XmlUtils;
import com.fugary.simple.api.web.vo.NameValue;
import com.fugary.simple.api.web.vo.query.ApiParamsVo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

/**
 * @author gary.fu
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HttpRequestUtils {

	private static final Logger logger = LoggerFactory.getLogger(HttpRequestUtils.class);

	private static final String UNKNOWN_KEY = "unknown";

	/**
	 * 取得访问IP
	 *
	 * @param request
	 * @return
	 */
	public static String getIp(HttpServletRequest request) {
		String ip = request.getHeader("x-forwarded-for");
		if (StringUtils.isBlank(ip) || UNKNOWN_KEY.equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if (StringUtils.isBlank(ip) || UNKNOWN_KEY.equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		if (StringUtils.isBlank(ip) || UNKNOWN_KEY.equalsIgnoreCase(ip)) {
			ip = request.getHeader("X-Real-IP");
		}
		if (StringUtils.isBlank(ip) || UNKNOWN_KEY.equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		return ip;
	}

	public static String getRequestUrl(HttpServletRequest request){
		StringBuffer url = request.getRequestURL();
		String queryString = request.getQueryString();
		if (StringUtils.isNotBlank(queryString)) {
			url.append("?").append(queryString);
		}
		return url.toString();
	}

	public static HttpSession getCurrentSession() {
		HttpServletRequest currentRequest = getCurrentRequest();
		if (currentRequest != null) {
			return currentRequest.getSession();
		}
		return null;
	}

	public static HttpServletRequest getCurrentRequest() {
		ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		return requestAttributes != null ? requestAttributes.getRequest() : null;
	}

	public static HttpServletResponse getCurrentResponse() {
		ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		return requestAttributes != null ? requestAttributes.getResponse() : null;
	}

	public static String getRequestPath(HttpServletRequest request) {
		String url = request.getServletPath();
		if (request.getPathInfo() != null) {
			url += request.getPathInfo();
		}
		return url;
	}

	public static String getClientIp() {
		HttpServletRequest currentRequest = getCurrentRequest();
		String ipAddr = null;
		if (currentRequest != null) {
			ipAddr = getIp(currentRequest);
			try {
				if (StringUtils.isNotBlank(ipAddr) && !ipAddr.contains(",") && InetAddress.getByName(ipAddr).isLoopbackAddress()) {
					ipAddr = InetAddress.getLocalHost().getHostAddress();
				}
			} catch (Exception e) {
				logger.error("获取IP错误", e);
			}
		}
		return ipAddr;
	}

	/**
	 * 获取本机IP地址
	 *
	 * @return
	 */
	public static String calcFirstLocalIp() {
		String localIp = StringUtils.EMPTY;
		try {
			InetAddress localhost = InetAddress.getLocalHost();
			localIp = localhost.getHostAddress();
		} catch (UnknownHostException e) {
			logger.error("获取本地IP地址错误", e);
		}
		if (StringUtils.isBlank(localIp)) {
			try {
				Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
				while (interfaces != null && interfaces.hasMoreElements()) {
					Enumeration<InetAddress> addresses = interfaces.nextElement().getInetAddresses();
					while (addresses != null && addresses.hasMoreElements()) {
						InetAddress address = addresses.nextElement();
						if (acceptableAddress(address)) {
							localIp = address.getHostAddress();
							break;
						}
					}
				}
			} catch (SocketException e) {
				logger.error("获取本地IP地址错误", e);
			}
		}
		return localIp;
	}

	/**
	 * 支持的地址信息
	 *
	 * @param address
	 * @return
	 */
	public static boolean acceptableAddress(InetAddress address) {
		return address != null && !address.isLoopbackAddress() && !address.isAnyLocalAddress() && !address.isLinkLocalAddress();
	}

	public static Object getJsonBody(String bodyStr) {
		if (JsonUtils.isJson(bodyStr)) {
			return JsonUtils.fromJson(bodyStr, Map.class);
		}
		return null;
	}

	public static Object getXmlBody(String bodyStr) {
		if (XmlUtils.isXml(bodyStr)) {
			return XmlUtils.fromXml(bodyStr, Map.class);
		}
		return null;
	}

	public static boolean isCompatibleWith(List<MediaType> mediaTypes, MediaType...matchTypes) {
		for (MediaType type : mediaTypes) {
			for (MediaType mediaType : matchTypes) {
				if (mediaType.isCompatibleWith(type)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 判断MediaType
	 * @param request
	 * @param matchTypes
	 * @return
	 */
	public static boolean isCompatibleWith(HttpServletRequest request, MediaType...matchTypes) {
		List<MediaType> mediaTypes = MediaType.parseMediaTypes(request.getContentType());
		return isCompatibleWith(mediaTypes, matchTypes);
	}

	/**
	 * 判断MediaType
	 * @param paramsVo
	 * @param matchTypes
	 * @return
	 */
	public static boolean isCompatibleWith(ApiParamsVo paramsVo, MediaType...matchTypes) {
		List<MediaType> mediaTypes = MediaType.parseMediaTypes(paramsVo.getContentType());
		return isCompatibleWith(mediaTypes, matchTypes);
	}

	public static Resource getBodyResource(HttpServletRequest request) throws IOException {
		Resource bodyResource = new InputStreamResource(request.getInputStream());
		if(request instanceof ContentCachingRequestWrapper){
			ContentCachingRequestWrapper contentCachingRequestWrapper = (ContentCachingRequestWrapper) request;
			if (contentCachingRequestWrapper.getContentAsByteArray().length > 0) {
				bodyResource = new ByteArrayResource(contentCachingRequestWrapper.getContentAsByteArray());
			}
		}
		return bodyResource;
	}

	/**
	 * 请求头获取
	 * @param request
	 */
	public static List<NameValue> getRequestHeaders(HttpServletRequest request){
		Enumeration<String> reqHeaders = request.getHeaderNames();
		ArrayList<NameValue> requestHeaders = new ArrayList<>();
		while (reqHeaders.hasMoreElements()) {
			String key = reqHeaders.nextElement();
			requestHeaders.add(new NameValue(key, request.getHeader(key)));
		}
		return requestHeaders;
	}

	/**
	 * 请求头获取
	 * @param request
	 */
	public static Map<String, String> getRequestHeadersMap(HttpServletRequest request){
		Enumeration<String> reqHeaders = request.getHeaderNames();
		Map<String, String> requestHeaders = new HashMap<>();
		while (reqHeaders.hasMoreElements()) {
			String key = reqHeaders.nextElement();
			requestHeaders.put(key, request.getHeader(key));
		}
		return requestHeaders;
	}
}
