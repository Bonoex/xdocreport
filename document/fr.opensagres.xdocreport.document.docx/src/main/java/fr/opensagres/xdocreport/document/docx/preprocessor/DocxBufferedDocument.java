/**
 * Copyright (C) 2011 Angelo Zerr <angelo.zerr@gmail.com> and Pascal Leclercq <pascal.leclercq@gmail.com>
 *
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package fr.opensagres.xdocreport.document.docx.preprocessor;

import static fr.opensagres.xdocreport.document.docx.DocxUtils.isBookmarkEnd;
import static fr.opensagres.xdocreport.document.docx.DocxUtils.isBookmarkStart;
import static fr.opensagres.xdocreport.document.docx.DocxUtils.isFldChar;
import static fr.opensagres.xdocreport.document.docx.DocxUtils.isFldSimple;
import static fr.opensagres.xdocreport.document.docx.DocxUtils.isHyperlink;
import static fr.opensagres.xdocreport.document.docx.DocxUtils.isP;
import static fr.opensagres.xdocreport.document.docx.DocxUtils.isR;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import fr.opensagres.xdocreport.core.utils.XMLUtils;
import fr.opensagres.xdocreport.document.docx.DocXConstants;
import fr.opensagres.xdocreport.document.docx.DocxUtils;
import fr.opensagres.xdocreport.document.preprocessor.sax.BufferedElement;
import fr.opensagres.xdocreport.document.preprocessor.sax.TransformedBufferedDocument;
import fr.opensagres.xdocreport.template.formatter.FieldMetadata;
import fr.opensagres.xdocreport.template.formatter.FieldsMetadata;

public class DocxBufferedDocument extends TransformedBufferedDocument implements
		DocXConstants {

	private final DocXBufferedDocumentContentHandler handler;
	private PBufferedRegion currentPRegion;
	private FldSimpleBufferedRegion currentFldSimpleRegion;
	private RBufferedRegion currentRRegion;
	private BookmarkBufferedRegion currentBookmark;
	private HyperlinkBufferedRegion currentHyperlink;

	public DocxBufferedDocument(DocXBufferedDocumentContentHandler handler) {
		this.handler = handler;
	}

	protected BufferedElement createElement(BufferedElement parent, String uri,
			String localName, String name, Attributes attributes)
			throws SAXException {

		if (isFldChar(uri, localName, name) && currentRRegion != null) {
			// w:fdlChar element
			String fldCharType = attributes.getValue(W_NS, FLDCHARTYPE_ATTR);
			currentRRegion.setFldCharType(fldCharType);
			return super
					.createElement(parent, uri, localName, name, attributes);
		}

		if (isP(uri, localName, name)) {
			// w:p element
			currentPRegion = new PBufferedRegion(getCurrentElement(), uri,
					localName, name, attributes);
			return currentPRegion;
		}

		if (isR(uri, localName, name) && currentFldSimpleRegion == null) {
			// w:r element
			currentRRegion = new RBufferedRegion(handler, parent, uri,
					localName, name, attributes);

			HyperlinkBufferedRegion hyperlink = getCurrentHyperlink();
			if (hyperlink != null) {
				hyperlink.addRegion(currentRRegion);
			} else {
				PBufferedRegion pRegion = getCurrentPRegion();
				if (pRegion != null) {
					pRegion.addRegion(currentRRegion);
				}
			}
			return currentRRegion;
		}

		if (isFldSimple(uri, localName, name)) {
			// w:fldSimple element
			// start of fldSimple mergefield, add the fieldName of mergefield
			// and ignore element
			String instrText = attributes.getValue(W_NS, INSTR_ATTR);
			FieldMetadata fieldAsTextStyling = handler
					.getFieldAsTextStyling(instrText);
			instrText = handler.processRowIfNeeded(attributes.getValue(W_NS,
					INSTR_ATTR));
			currentFldSimpleRegion = new FldSimpleBufferedRegion(handler,
					parent, uri, localName, name, attributes);
			currentFldSimpleRegion.setInstrText(instrText, fieldAsTextStyling);
			// boolean addElement = false;
			if (currentFldSimpleRegion.getFieldName() == null) {
				// super.doStartElement(uri, localName, name, attributes);
				// addElement = true;
			} 
			// currentRegion = currentFldSimpleRegion;
			return currentFldSimpleRegion;
		}

		if (isBookmarkStart(uri, localName, name)) {
			// <w:bookmarkStart w:id="0" w:name="logo" />
			String bookmarkName = attributes.getValue(W_NS, NAME_ATTR);
			FieldsMetadata fieldsMetadata = handler.getFieldsMetadata();
			if (fieldsMetadata != null) {
				String imageFieldName = fieldsMetadata
						.getImageFieldName(bookmarkName);
				if (imageFieldName != null) {
					currentBookmark = new BookmarkBufferedRegion(bookmarkName,
							imageFieldName, parent, uri, localName,
							imageFieldName, attributes);
					return currentBookmark;
				}
			}
			return super
					.createElement(parent, uri, localName, name, attributes);
		}

		if (isBookmarkEnd(uri, localName, name)) {
			// w:bookmarkEnd
			// boolean result = super.doStartElement(uri, localName, name,
			// attributes);
			// if (currentBookmark != null) {
			// currentRegion = currentBookmark.getParent();
			// }
			currentBookmark = null;
			return super
					.createElement(parent, uri, localName, name, attributes);
			// return result;
		}

		if (isHyperlink(uri, localName, name)) {
			// <w:hyperlink r:id="rId5" w:history="1">

			int idIndex = attributes.getIndex(R_NS, ID_ATTR);
			if (idIndex != -1) {
				String attrName = attributes.getQName(idIndex);
				AttributesImpl attributesImpl = XMLUtils
						.toAttributesImpl(attributes);
				attributesImpl.removeAttribute(idIndex);
				String id = attributes.getValue(idIndex);
				currentHyperlink = new HyperlinkBufferedRegion(handler, parent,
						uri, localName, name, attributesImpl);
				currentHyperlink.setId(attrName, id);
				// return true;
				return currentHyperlink;
			}
			return super
					.createElement(parent, uri, localName, name, attributes);

		}
		return super.createElement(parent, uri, localName, name, attributes);
	}

	public BookmarkBufferedRegion getCurrentBookmark() {
		return currentBookmark;
	}

	public FldSimpleBufferedRegion getCurrentFldSimpleRegion() {
		return currentFldSimpleRegion;
	}

	public HyperlinkBufferedRegion getCurrentHyperlink() {
		return currentHyperlink;
	}

	public PBufferedRegion getCurrentPRegion() {
		return currentPRegion;
	}

	public RBufferedRegion getCurrentRRegion() {
		return currentRRegion;
	}

	@Override
	public void onEndEndElement(String uri, String localName, String name) {
		if (isP(uri, localName, name) && currentPRegion != null) {
			super.onEndEndElement(uri, localName, name);
			currentPRegion.process();
			// currentRegion = currentPRegion.getParent();
			currentPRegion = null;
			return;
		}

		if (isR(uri, localName, name) && currentRRegion != null
				&& currentFldSimpleRegion == null) {
			super.onEndEndElement(uri, localName, name);
			boolean hasScript = processScriptBeforeAfter(currentRRegion);
			if (hasScript) {
				currentRRegion.reset();
			}
			currentRRegion = null;
			return;
		}

		if (isFldSimple(uri, localName, name) && currentFldSimpleRegion != null) {
			// it's end of fldSimple and it's Mergefield; ignore the element
			boolean hasScript = processScriptBeforeAfter(currentFldSimpleRegion);
			if (hasScript) {
				currentFldSimpleRegion.reset();
			}
			super.onEndEndElement(uri, localName, name);
			// currentRegion = currentFldSimpleRegion.getParent();
			currentFldSimpleRegion = null;
			return;
		}

		if (isHyperlink(uri, localName, name)) {
			// </w:hyperlink>
			HyperlinkBufferedRegion hyperlink = (HyperlinkBufferedRegion) currentHyperlink;
			super.onEndEndElement(uri, localName, name);
			hyperlink.process();
			return;
		}

		super.onEndEndElement(uri, localName, name);
	}

	private boolean processScriptBeforeAfter(MergefieldBufferedRegion mergefield) {
		String fieldName = mergefield.getFieldName();
		if (fieldName == null) {
			return false;
		} else {
			boolean hasScript = handler.processScriptBefore(fieldName);
			if (hasScript) {
				return hasScript;
			} else {
				return handler.processScriptAfter(fieldName);
			}
		}
	}

	@Override
	protected boolean isTable(String uri, String localName, String name) {
		return DocxUtils.isTable(uri, localName, name);
	}

	@Override
	protected boolean isTableRow(String uri, String localName, String name) {
		return DocxUtils.isTableRow(uri, localName, name);
	}

}
