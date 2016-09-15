package eu.daiad.web.controller.action;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import eu.daiad.web.controller.BaseController;
import eu.daiad.web.domain.application.Utility;
import eu.daiad.web.domain.application.WeatherServiceEntity;
import eu.daiad.web.domain.application.WeatherServiceUtilityEntity;
import eu.daiad.web.model.RestResponse;
import eu.daiad.web.model.error.WeatherErrorCode;
import eu.daiad.web.model.security.AuthenticatedUser;
import eu.daiad.web.model.utility.UtilityInfo;
import eu.daiad.web.model.weather.WeatherQueryResponse;
import eu.daiad.web.model.weather.WeatherService;
import eu.daiad.web.model.weather.WeatherServiceResponse;
import eu.daiad.web.service.weather.DailyWeatherData;
import eu.daiad.web.service.weather.HourlyWeatherData;
import eu.daiad.web.service.weather.IWeatherRepository;

/**
 * Provides actions for querying weather services and data.
 */
@RestController
public class WeatherController extends BaseController {

    private static final Log logger = LogFactory.getLog(WeatherController.class);

    @Autowired
    private IWeatherRepository weatherRepository;

    @RequestMapping(value = "/action/weather/service", method = RequestMethod.GET, produces = "application/json")
    @Secured("ROLE_USER, ROLE_SUPERUSER, ROLE_ADMIN")
    public RestResponse getTrialUserActivity(@AuthenticationPrincipal AuthenticatedUser user) {
        try {
            WeatherServiceResponse weatherServiceResponse = new WeatherServiceResponse();

            List<WeatherServiceEntity> services = weatherRepository.getServices();

            for (WeatherServiceEntity serviceEntity : services) {
                WeatherService service = new WeatherService();

                service.setId(serviceEntity.getId());
                service.setName(serviceEntity.getName());

                for (WeatherServiceUtilityEntity utilityEntity : serviceEntity.getUtilities()) {
                    service.getUtilities().add(new UtilityInfo(utilityEntity.getUtility()));
                }

                weatherServiceResponse.getServices().add(service);
            }

            return weatherServiceResponse;
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);

            RestResponse response = new RestResponse();
            response.add(this.getError(ex));

            return response;
        }
    }

    @RequestMapping(value = "/action/weather/{service}/{utility}/{interval}/{from}/{to}", method = RequestMethod.GET, produces = "application/json")
    @Secured("ROLE_USER, ROLE_SUPERUSER, ROLE_ADMIN")
    public RestResponse getTrialUserActivity(@AuthenticationPrincipal AuthenticatedUser user,
                    @PathVariable String service, @PathVariable String utility, @PathVariable String interval,
                    @PathVariable String from, @PathVariable String to) {
        try {
            WeatherQueryResponse weatherQueryResponse = new WeatherQueryResponse();

            // Check if service exists
            WeatherServiceEntity weatherServiceEntity;

            if (StringUtils.isNumeric(service)) {
                weatherServiceEntity = weatherRepository.getServiceById(Integer.parseInt(service));
            } else {
                weatherServiceEntity = weatherRepository.getServiceByName(service);
            }
            if (weatherServiceEntity == null) {
                weatherQueryResponse.add(this.getError(WeatherErrorCode.SERVICE_NOT_FOUND));

                return weatherQueryResponse.toRestResponse();
            }

            // Check if utility is supported by the service
            Utility utilityEntity = null;

            if (!StringUtils.isBlank(utility)) {
                Integer utilityId = null;

                if (StringUtils.isNumeric(utility)) {
                    utilityId = Integer.parseInt(utility);
                }

                for (WeatherServiceUtilityEntity weatherServiceUtilityEntity : weatherServiceEntity.getUtilities()) {
                    if (utilityId != null) {
                        if (weatherServiceUtilityEntity.getUtility().getId() == utilityId) {
                            utilityEntity = weatherServiceUtilityEntity.getUtility();
                            break;
                        }
                    } else {
                        if (weatherServiceUtilityEntity.getUtility().getName().toLowerCase().equals(
                                        utility.toLowerCase())) {
                            utilityEntity = weatherServiceUtilityEntity.getUtility();
                            break;
                        }
                    }
                }
            }

            if (utilityEntity == null) {
                weatherQueryResponse.add(this.getError(WeatherErrorCode.UTILITY_NOT_FOUND));

                return weatherQueryResponse.toRestResponse();
            }

            // Parse dates and get weather data
            DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyyMMdd").withZone(DateTimeZone.UTC);

            List<DailyWeatherData> days = null;
            List<HourlyWeatherData> hours = null;

            switch (interval) {
                case "day":
                    days = weatherRepository.getDailyData(weatherServiceEntity.getId(), utilityEntity.getId(),
                                    formatter.parseDateTime(from), formatter.parseDateTime(to));
                    break;
                case "hour":
                    hours = weatherRepository.getHourlyData(weatherServiceEntity.getId(), utilityEntity.getId(),
                                    formatter.parseDateTime(from), formatter.parseDateTime(to));
                    break;
            }

            weatherQueryResponse.setDays(days);
            weatherQueryResponse.setHours(hours);

            return weatherQueryResponse;
        } catch (IllegalArgumentException ex) {
            logger.error(ex.getMessage(), ex);

            RestResponse response = new RestResponse();
            response.add(this.getError(WeatherErrorCode.INVALID_DATETIME));

            return response;
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);

            RestResponse response = new RestResponse();
            response.add(this.getError(ex));

            return response;
        }
    }
}