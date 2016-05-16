package eu.daiad.web.repository.application;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.icu.text.MessageFormat;

import eu.daiad.web.domain.application.AccountAlert;
import eu.daiad.web.domain.application.AccountAlertProperty;
import eu.daiad.web.domain.application.AccountDynamicRecommendation;
import eu.daiad.web.domain.application.AccountDynamicRecommendationProperty;
import eu.daiad.web.domain.application.AlertTranslation;
import eu.daiad.web.domain.application.DynamicRecommendationTranslation;
import eu.daiad.web.domain.application.StaticRecommendation;
import eu.daiad.web.model.error.ApplicationException;
import eu.daiad.web.model.error.SharedErrorCode;
import eu.daiad.web.model.message.EnumAlertType;
import eu.daiad.web.model.message.EnumDynamicRecommendationType;
import eu.daiad.web.model.message.EnumMessageType;
import eu.daiad.web.model.message.Message;
import eu.daiad.web.model.security.AuthenticatedUser;

@Repository
@Transactional("transactionManager")
public class JpaMessageRepository implements IMessageRepository {

	@PersistenceContext(unitName = "default")
	EntityManager entityManager;

	private final int staticTipInterval = DateTimeConstants.DAYS_PER_WEEK;
	private final String currencyKey1 = "currency1";
	private final String currencyKey2 = "currency2";

	private AuthenticatedUser getCurrentAuthenticatedUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth.getPrincipal() instanceof AuthenticatedUser) {
			return (AuthenticatedUser) auth.getPrincipal();
		} else {
			throw new ApplicationException(SharedErrorCode.AUTHORIZATION_ANONYMOUS_SESSION);
		}
	}

	@Override
	public void setMessageAcknowledgement(EnumMessageType type, int id, DateTime acknowledgedOn) {
		switch (type) {
			case ALERT:
				persistAlertAcknowledgement(id, acknowledgedOn);
				break;
			case RECOMMENDATION_DYNAMIC:
				persistDynamicRecommendationAcknowledgement(id, acknowledgedOn);
				break;
			case RECOMMENDATION_STATIC:
				break;
			case ANNOUNCEMENT:
				throw new ApplicationException(SharedErrorCode.NOT_IMPLEMENTED);
			default:
				throw new ApplicationException(SharedErrorCode.UNKNOWN).set("Unknown message acknowledgement type.",
								type);
		}
	}

	@Override
	public List<Message> getMessages() {
		AuthenticatedUser user = this.getCurrentAuthenticatedUser();

		String locale = resolveLocale(user.getLocale());

		Locale currencySymbol = resolveCurrency(user.getCountry());

		List<Message> messages = new ArrayList<>();

		// Get alerts
		HashSet<Integer> uniqueAlerts = new HashSet<>();

		TypedQuery<eu.daiad.web.domain.application.AccountAlert> accountAlertsQuery = entityManager.createQuery(
						"select a from account_alert a "
										+ "where a.account.id = :accountId and a.acknowledgedOn is null "
										+ "order by a.createdOn desc",
						eu.daiad.web.domain.application.AccountAlert.class);

		accountAlertsQuery.setParameter("accountId", user.getId());

		for (AccountAlert accountAlert : accountAlertsQuery.getResultList()) {
			// Find translation by locale
			AlertTranslation alertTranslation = null;

			for (AlertTranslation translation : accountAlert.getAlert().getTranslations()) {
				if (translation.getLocale().equals(locale)) {
					alertTranslation = translation;
					break;
				}

			}

			if (alertTranslation == null) {
				continue;
			}

			// Ignore duplicate alerts. Include only the most recent one per
			// type.
			if (!uniqueAlerts.contains(alertTranslation.getAlert().getId())) {
				// Build localized strings using translation and properties
				Map<String, String> formatProperties = new HashMap<>();

				for (AccountAlertProperty property : accountAlert.getProperties()) {
					setFormatProperties(property.getKey(), property.getValue(), formatProperties, currencySymbol);
				}

				MessageFormat titleTemplate = new MessageFormat(alertTranslation.getTitle(), currencySymbol);
				String title = titleTemplate.format(formatProperties);

				// Create message
				String description = null;

				if (alertTranslation.getDescription() != null) {
					MessageFormat descriptionTemplate = new MessageFormat(alertTranslation.getDescription(),
									new Locale(locale));
					description = descriptionTemplate.format(formatProperties);
				}
				eu.daiad.web.model.message.Alert message = new eu.daiad.web.model.message.Alert(
								EnumAlertType.fromInteger(accountAlert.getAlert().getId()));

				message.setId(accountAlert.getId());
				message.setPriority(accountAlert.getAlert().getPriority());
				message.setTitle(title);
				message.setDescription(description);
				message.setImageLink(alertTranslation.getImageLink());
				message.setCreatedOn(accountAlert.getCreatedOn().getMillis());

				messages.add(message);

				uniqueAlerts.add(alertTranslation.getAlert().getId());
			}
		}

		// Get dynamic recommendations
		HashSet<Integer> uniqueRecommendations = new HashSet<>();

		TypedQuery<eu.daiad.web.domain.application.AccountDynamicRecommendation> accountRecommendationQuery = entityManager
						.createQuery("select a from account_dynamic_recommendation a "
										+ "where a.account.id = :accountId and a.acknowledgedOn is null "
										+ "order by a.createdOn desc",
										eu.daiad.web.domain.application.AccountDynamicRecommendation.class);

		accountRecommendationQuery.setParameter("accountId", user.getId());

		for (AccountDynamicRecommendation accountRecommendation : accountRecommendationQuery.getResultList()) {
			// Find translation by locale
			DynamicRecommendationTranslation recommendationTranslation = null;

			for (DynamicRecommendationTranslation translation : accountRecommendation.getRecommendation()
							.getTranslations()) {
				if (translation.getLocale().equals(locale)) {
					recommendationTranslation = translation;
					break;
				}

			}

			if (recommendationTranslation == null) {
				continue;
			}

			// Ignore duplicate recommendations. Include only the most recent
			// one per type.
			if (!uniqueRecommendations.contains(recommendationTranslation.getRecommendation().getId())) {
				// Build localized strings using translation and properties
				Map<String, String> formatProperties = new HashMap<>();

				for (AccountDynamicRecommendationProperty property : accountRecommendation.getProperties()) {
					setFormatProperties(property.getKey(), property.getValue(), formatProperties, currencySymbol);
				}

				MessageFormat titleTemplate = new MessageFormat(recommendationTranslation.getTitle(), currencySymbol);
				String title = titleTemplate.format(formatProperties);

				MessageFormat descriptionTemplate = new MessageFormat(recommendationTranslation.getDescription());
				String description = descriptionTemplate.format(formatProperties);

				// Create recommendation
				eu.daiad.web.model.message.DynamicRecommendation message = new eu.daiad.web.model.message.DynamicRecommendation(
								EnumDynamicRecommendationType.fromInteger(accountRecommendation.getRecommendation()
												.getId()));

				message.setId(accountRecommendation.getId());
				message.setPriority(accountRecommendation.getRecommendation().getPriority());
				message.setTitle(title);
				message.setDescription(description);
				message.setImageLink(recommendationTranslation.getImageLink());
				message.setCreatedOn(accountRecommendation.getCreatedOn().getMillis());

				messages.add(message);

				uniqueRecommendations.add(recommendationTranslation.getRecommendation().getId());
			}
		}

		// Add a random static tip every week.
		if ((user.getStaticTipSentOn() == null)
						|| (user.getStaticTipSentOn().isBefore(DateTime.now().minusDays(staticTipInterval)))) {

			for (Message advisoryMessage : getAdvisoryMessages(user.getLocale())) {
				messages.add(advisoryMessage);
			}
		}
		return messages;
	}

	@Override
	public List<Message> getAdvisoryMessages(String locale) {
		List<Message> messages = new ArrayList<>();

		switch (locale) {
			case "en":
			case "es":
				// Ignore
				break;
			default:
				// Set default
				locale = "en";
		}

		TypedQuery<eu.daiad.web.domain.application.StaticRecommendation> accountAlertsQuery = entityManager
						.createQuery("select a from static_recommendation a where a.locale = :locale",
										eu.daiad.web.domain.application.StaticRecommendation.class);
		accountAlertsQuery.setParameter("locale", locale);

		for (StaticRecommendation staticRecommendation : accountAlertsQuery.getResultList()) {
			eu.daiad.web.model.message.StaticRecommendation message = new eu.daiad.web.model.message.StaticRecommendation();

			message.setId(staticRecommendation.getId());
			message.setIndex(staticRecommendation.getIndex());
			message.setTitle(staticRecommendation.getTitle());
			message.setDescription(staticRecommendation.getDescription());
			message.setImageEncoded(staticRecommendation.getImage());
			message.setImageLink(staticRecommendation.getImageLink());
			message.setPrompt(staticRecommendation.getPrompt());
			message.setExternaLink(staticRecommendation.getExternaLink());
			message.setSource(staticRecommendation.getSource());
			if (staticRecommendation.getCreatedOn() != null) {
				message.setCreatedOn(staticRecommendation.getCreatedOn().getMillis());
			}
			if (staticRecommendation.getModifiedOn() != null) {
				message.setModifiedOn(staticRecommendation.getModifiedOn().getMillis());
			}
			message.setActive(staticRecommendation.isActive());

			messages.add(message);
		}

		return messages;
	}

	// TODO : When sending an acknowledgement for an alert of a specific type,
	// an older (not acknowledged) alert of the same type may appear in the next
	// get messages call

	private void persistAlertAcknowledgement(int id, DateTime acknowledgedOn) {
		AuthenticatedUser user = this.getCurrentAuthenticatedUser();

		TypedQuery<eu.daiad.web.domain.application.AccountAlert> accountAlertsQuery = entityManager
						.createQuery("select a from account_alert a "
										+ "where a.account.id = :accountId and a.id = :alertId and a.acknowledgedOn is null",
										eu.daiad.web.domain.application.AccountAlert.class);

		accountAlertsQuery.setParameter("accountId", user.getId());
		accountAlertsQuery.setParameter("alertId", id);

		List<AccountAlert> alerts = accountAlertsQuery.getResultList();

		if (alerts.size() == 1) {
			alerts.get(0).setAcknowledgedOn(acknowledgedOn);
		}
	}

	private void persistDynamicRecommendationAcknowledgement(int id, DateTime acknowledgedOn) {
		AuthenticatedUser user = this.getCurrentAuthenticatedUser();

		TypedQuery<eu.daiad.web.domain.application.AccountDynamicRecommendation> accountDynamicRecommendationQuery = entityManager
						.createQuery("select a from account_dynamic_recommendation a "
										+ "where a.account.id = :accountId and a.id = :dynamicRecommendationId and a.acknowledgedOn is null",
										eu.daiad.web.domain.application.AccountDynamicRecommendation.class);

		accountDynamicRecommendationQuery.setParameter("accountId", user.getId());
		accountDynamicRecommendationQuery.setParameter("dynamicRecommendationId", id);

		List<AccountDynamicRecommendation> recommendations = accountDynamicRecommendationQuery.getResultList();

		if (recommendations.size() == 1) {
			recommendations.get(0).setAcknowledgedOn(acknowledgedOn);
		}
	}

	private Locale resolveCurrency(String country) {
		Locale currency;

		// TODO: check fixed values of countries
		switch (country) {
			case "United Kingdom":
				// currencyRate = "GBP";
				currency = Locale.UK;
				break;
			default:
				// currencyRate = "EUR";
				currency = Locale.GERMANY;
		}
		return currency;
	}

	private String resolveLocale(String locale) {
		switch (locale) {
			case "en":
			case "es":
				// Ignore
				break;
			default:
				// Set default
				locale = "en";
				break;
		}
		return locale;
	}

	private void setFormatProperties(String key, String value, Map<String, String> formatProperties,
					Locale currencySymbol) {
		switch (key) {
			case currencyKey1: {
				NumberFormat numberFormat = NumberFormat.getCurrencyInstance(currencySymbol);
				numberFormat.setMaximumFractionDigits(1);
				float euros = Float.parseFloat(value);
				float money = convertCurrencyIfNeed(euros, currencySymbol);

				String currencyFormatted = numberFormat.format(money);
				formatProperties.put(value, currencyFormatted);
				break;
			}
			case currencyKey2: {
				NumberFormat numberFormat = NumberFormat.getCurrencyInstance(currencySymbol);
				numberFormat.setMaximumFractionDigits(1);
				float euros = Float.parseFloat(value);
				float money = convertCurrencyIfNeed(euros, currencySymbol);

				String currencyFormatted = numberFormat.format(money);
				formatProperties.put(key, currencyFormatted);
				break;
			}
			default:
				formatProperties.put(key, value);
				break;
		}
	}

	private float convertCurrencyIfNeed(float euros, Locale currencySymbol) {
		// this is dummy method for future use. Currently returns only euros.
		// The currency is converted in the message computation for now and only
		// for KWH prices
		if (currencySymbol.equals(Locale.GERMANY)) {
			return euros;
		} else if (currencySymbol.equals(Locale.UK)) {
			return euros;
			// return (float) (euros*0.8); //get currency rate from db
		} else {
			return euros;
		}
	}

	// private float convertEURtoGBP(float euros){
	// TypedQuery<eu.daiad.web.domain.Currency> query = entityManager
	// .createQuery("select a from currency a where a.ISO_code = :ISO_code",
	// eu.daiad.web.domain.Currency.class).setFirstResult(0)
	// .setMaxResults(1);
	// query.setParameter("ISO_code", "EUR");
	// Currency c = query.getSingleResult();
	// float p = c.getToGbp();
	//
	// return 4;
	// }

}
