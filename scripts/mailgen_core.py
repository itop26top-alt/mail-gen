import os
import email
from email.header import decode_header

def parse_incoming_email(raw_email_bytes):
    """
    Парсит сырое письмо (MIME) и извлекает полную информацию,
    включая тело письма (HTML/Text) и прикрепленные файлы/изображения.
    """
    msg = email.message_from_bytes(raw_email_bytes)
    
    subject = ""
    if msg["Subject"]:
        decoded_subject = decode_header(msg["Subject"])
        subject, encoding = decoded_subject[0]
        if isinstance(subject, bytes):
            subject = subject.decode(encoding or "utf-8", errors="ignore")

    sender = msg.get("From", "")
    recipient = msg.get("To", "")
    
    body = ""
    attachments = []

    if msg.is_multipart():
        for part in msg.walk():
            content_type = part.get_content_type()
            content_disposition = str(part.get("Content-Disposition"))

            if "attachment" in content_disposition or "inline" in content_disposition:
                filename = part.get_filename()
                if filename:
                    attachment_data = part.get_payload(decode=True)
                    file_path = os.path.join("/tmp", filename)
                    with open(file_path, "wb") as f:
                        f.write(attachment_data)
                    
                    attachments.append({
                        "fileName": filename,
                        "mimeType": content_type,
                        "url": f"/api/attachments/{filename}"
                    })
            elif content_type == "text/html" and not body:
                payload = part.get_payload(decode=True)
                if payload:
                    body = payload.decode(part.get_content_charset() or "utf-8", errors="ignore")
            elif content_type == "text/plain" and not body:
                payload = part.get_payload(decode=True)
                if payload:
                    body = payload.decode(part.get_content_charset() or "utf-8", errors="ignore")
    else:
        payload = msg.get_payload(decode=True)
        if payload:
            body = payload.decode(msg.get_content_charset() or "utf-8", errors="ignore")

    return {
        "subject": subject,
        "sender": sender,
        "recipient": recipient,
        "body": body,
        "snippet": body[:100] + "..." if len(body) > 100 else body,
        "attachments": attachments
    }
