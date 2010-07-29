package geb.navigator

import java.util.regex.Pattern
import org.apache.commons.lang.NotImplementedException
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.internal.FindsByCssSelector
import static java.util.Collections.EMPTY_LIST

class NonEmptyNavigator extends Navigator {

	static {
		def mc = new AttributeAccessingMetaClass(new ExpandoMetaClass(NonEmptyNavigator))
		mc.initialize()
		NonEmptyNavigator.metaClass = mc
	}

	private final List<WebElement> contextElements

	NonEmptyNavigator(WebElement... contextElements) {
		this.contextElements = contextElements as List
	}

	NonEmptyNavigator(Collection<? extends WebElement> contextElements) {
		this.contextElements = contextElements as List
	}

	Navigator find(String selectorString) {
		if (contextElements.head() instanceof FindsByCssSelector) {
			List<WebElement> list = []
			contextElements.each {
				list.addAll it.findElements(By.cssSelector(selectorString))
			}
			on(list)
		} else {
			on CssSelector.findByCssSelector(allElements(), selectorString)
		}
	}

	Navigator find(Map<String, Object> predicates) {
		List<WebElement> list = []
		contextElements*.findElements(By.xpath("descendant::*")).flatten().each { WebElement element ->
			if (matches(element, predicates)) {
				list << element
			}
		}
		on list
	}

	Navigator find(Map<String, Object> predicates, String selector) {
		find(selector).filter(predicates)
	}

	private boolean matches(WebElement element, Map<String, Object> predicates) {
		return predicates.every { name, requiredValue ->
			def actualValue = name == "text" ? element.text : element.getAttribute(name)
			if (requiredValue instanceof Pattern) {
				actualValue ==~ requiredValue
			} else {
				actualValue == requiredValue
			}
		}
	}

	Navigator filter(String selectorString) {
		on contextElements.findAll { element ->
			CssSelector.matches(element, selectorString)
		}
	}

	Navigator filter(Map<String, Object> predicates) {
		on contextElements.findAll { matches(it, predicates) }
	}

	Navigator filter(Map<String, Object> predicates, String selector) {
		filter(selector).filter(predicates)
	}

	Navigator getAt(int index) {
		on getElement(index)
	}

	Navigator getAt(Range range) {
		on getElements(range)
	}

	Navigator getAt(EmptyRange range) {
		EmptyNavigator.instance
	}

	Navigator getAt(Collection indexes) {
		on getElements(indexes)
	}

	Collection<WebElement> allElements() {
		contextElements as WebElement[]
	}

	WebElement getElement(int index) {
		contextElements[index]
	}

	List<WebElement> getElements(Range range) {
		contextElements[range]
	}

	List<WebElement> getElements(EmptyRange range) {
		EMPTY_LIST
	}

	List<WebElement> getElements(Collection indexes) {
		contextElements[indexes]
	}

	Navigator remove(int index) {
		int size = size()
		if (!(index in -size..<size)) {
			this
		} else if (size == 1) {
			EmptyNavigator.instance
		} else {
			on(contextElements - contextElements[index])
		}
	}

	Navigator next() {
		on collectElements {
			it.findElement By.xpath("following-sibling::*")
		}
	}

	Navigator next(String selectorString) {
		on collectElements {
			def siblings = it.findElements(By.xpath("following-sibling::*"))
			siblings.find { CssSelector.matches(it, selectorString) }
		}
	}

	Navigator previous() {
		on collectElements {
			def siblings = it.findElements(By.xpath("preceding-sibling::*"))
			siblings ? siblings.last() : EMPTY_LIST
		}
	}

	Navigator previous(String selectorString) {
		on collectElements {
			def siblings = it.findElements(By.xpath("preceding-sibling::*")).reverse()
			siblings.find { CssSelector.matches(it, selectorString) }
		}
	}

	Navigator parent() {
		on collectElements {
			it.findElement By.xpath("parent::*")
		}
	}

	Navigator parent(String selectorString) {
		on collectElements {
			def parents = it.findElements(By.xpath("ancestor::*")).reverse()
			parents.find { CssSelector.matches(it, selectorString) }
		}
	}

	Navigator unique() {
		new NonEmptyNavigator(contextElements.unique())
	}

	boolean hasClass(String valueToContain) {
		valueToContain in classNames
	}

	boolean is(String tag) {
		contextElements.any { tag.equalsIgnoreCase(it.tagName) }
	}

	String getTag() {
		firstElement().tagName
	}

	String getText() {
		firstElement().text
	}

	String getAttribute(String name) {
		firstElement().getAttribute(name) ?: ""
	}

	Collection<String> getClassNames() {
		def classNames = new HashSet<String>()
		contextElements.collect {
			def value = it.getAttribute("class")
			if (value) classNames.addAll value.tokenize()
		}
		classNames
	}

	def value() {
		getInputValues(contextElements)
	}

	Navigator value(value) {
		setInputValues(contextElements, value)
		this
	}

	String[] values() {
		def list = []
		contextElements.collect { element ->
			if (element.tagName ==~ /(?i)select/) {
				def options = element.findElements(By.tagName("option")).findAll { it.isSelected() }
				list.addAll options.value
			} else {
				list << element.value
			}
		}
		list as String[]
	}

	void click() {
		throw new NotImplementedException()
	}

	int size() {
		contextElements.size()
	}

	boolean isEmpty() {
		size() == 0
	}

	Navigator head() {
		first()
	}

	Navigator first() {
		on firstElement()
	}

	Navigator last() {
		on lastElement()
	}

	Navigator tail() {
		on contextElements.tail()
	}

	Navigator verifyNotEmpty() {
		this
	}

	String toString() {
		contextElements*.toString()
	}

	def propertyMissing(String name) {
		switch (name) {
			case ~/@.+/:
				return getAttribute(name.substring(1))
			default:
				def inputs = collectElements {
					it.findElements(By.name(name))
				}

				if (inputs) {
					return getInputValues(inputs)
				} else {
					throw new MissingPropertyException(name, getClass())
				}
		}
	}

	def propertyMissing(String name, value) {
		def inputs = collectElements {
			it.findElements(By.name(name))
		}

		if (inputs) {
			setInputValues(inputs, value)
		} else {
			throw new MissingPropertyException(name, getClass())
		}
	}

	private getInputValues(Collection<WebElement> inputs) {
		def values = []
		inputs.each { WebElement input ->
			def value = getInputValue(input)
			if (value) values << value
		}
		return values.size() < 2 ? values[0] : values
	}

	private getInputValue(WebElement input) {
		def value = null
		if (input.tagName == "select") {
			if (input.getAttribute("multiple")) {
				value = input.findElements(By.tagName("option")).findAll { it.isSelected() }*.value
			} else {
				value = input.findElements(By.tagName("option")).find { it.isSelected() }.value
			}
		} else if (input.getAttribute("type") in ["checkbox", "radio"]) {
			if (input.isSelected()) {
				value = input.value
			}
		} else {
			value = input.value
		}
		value
	}

	private void setInputValues(Collection<WebElement> inputs, value) {
		inputs.each { WebElement input ->
			setInputValue(input, value)
		}
	}

	private void setInputValue(WebElement input, value) {
		if (input.tagName == "select") {
			if (input.getAttribute("multiple")) {
				input.findElements(By.tagName("option")).each { WebElement option ->
					if (option.value in value) {
						option.setSelected()
					} else if (option.isSelected()) {
						option.toggle()
					}
				}
			} else {
				input.findElements(By.tagName("option")).find { it.value == value }.setSelected()
			}
		} else if (input.getAttribute("type") == "checkbox") {
			if (input.value == value) {
				input.setSelected()
			} else if (input.isSelected()) {
				input.toggle()
			}
		} else if (input.getAttribute("type") == "radio") {
			if (input.value == value) {
				input.setSelected()
			}
		} else {
			input.clear()
			input.sendKeys value
		}
	}

	private WebElement firstElementInContext(Closure closure) {
		def result = null
		for (int i = 0; !result && i < contextElements.size(); i++) {
			try {
				result = closure(contextElements[i])
			} catch (org.openqa.selenium.NoSuchElementException e) { }
		}
		result
	}

	private List<WebElement> collectElements(Closure closure) {
		List<WebElement> list = []
		contextElements.each {
			try {
				def value = closure(it)
				switch (value) {
					case Collection:
						list.addAll value
						break
					default:
						if (value) list << value
				}
			} catch (org.openqa.selenium.NoSuchElementException e) { }
		}
		list
	}

}
