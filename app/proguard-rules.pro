# R8 rules for release builds. Libraries ship their own consumer rules; add
# project-specific rules here with a comment explaining why each is needed.

# PdfBox-Android decodes JPEG 2000 (JPX) images only when the optional
# com.gemalto.jp2 library is present. R Reader does not ship it (text extraction
# never decodes images), so PdfBox's references to it must not fail R8.
-dontwarn com.gemalto.jp2.**
