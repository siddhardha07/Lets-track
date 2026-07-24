#!/usr/bin/env python3
"""
IDFC First Bank Statement Parser - Table-based approach
Uses pdfplumber.extract_tables() to get structured transaction data
"""
import pdfplumber
import re
import json
import sys
from datetime import datetime

def parse_amount(amount_str):
    """Parse amount string to float"""
    if not amount_str or amount_str.strip() == '':
        return None
    # Remove commas and CR suffix
    cleaned = amount_str.replace(',', '').replace('CR', '').replace('DR', '').strip()
    try:
        return float(cleaned)
    except ValueError:
        return None

def parse_date(date_str):
    """Parse date string to standard format"""
    if not date_str:
        return None
    
    # Clean the date string first
    date_str = clean_text(date_str)
    
    # Handle formats like "01 Jun 26 10:21" or "01 Jun 26"
    match = re.match(r'(\d{2})\s+(\w{3})\s+(\d{2})(?:\s+\d{2}:\d{2})?', date_str)
    if match:
        day, month_abbr, year = match.groups()
        # Convert to full year
        year = f"20{year}"
        return f"{day} {month_abbr} {year}"
    return date_str

def clean_text(text):
    """Clean text by removing excessive whitespace and newlines"""
    if not text:
        return ""
    # Replace newlines and multiple spaces with single space
    cleaned = re.sub(r'\s+', ' ', text.strip())
    return cleaned

def extract_merchant(details):
    """Extract merchant name from transaction details"""
    if not details:
        return "Unknown"
    
    # Clean the details first
    details = clean_text(details)
    
    # UPI patterns: UPI/DR/ref/MERCHANT/...
    match = re.search(r'UPI/[CD]R/\d+/([^/]+)', details)
    if match:
        return match.group(1).strip()
    
    # NACH pattern
    if 'NACH' in details:
        match = re.search(r'NACH/([^/]+)', details)
        if match:
            return match.group(1).strip()
    
    # Interest
    if 'INTEREST' in details:
        return "Interest Credit"
    
    # Take first meaningful word
    words = details.split()
    if words:
        return words[0]
    
    return "Unknown"

def extract_tables_from_pdf(pdf_path, password):
    """Extract all tables from PDF"""
    all_tables = []
    with pdfplumber.open(pdf_path, password=password) as pdf:
        for page_num, page in enumerate(pdf.pages, 1):
            tables = page.extract_tables()
            if tables:
                print(f"Page {page_num}: Found {len(tables)} table(s)")
                all_tables.extend(tables)
    return all_tables

def parse_summary_from_table(tables):
    """Extract summary information from tables"""
    summary = {
        'opening_balance': None,
        'closing_balance': None,
        'num_withdrawals': None,
        'num_deposits': None,
        'total_withdrawals': None,
        'total_deposits': None
    }
    
    for table in tables:
        for i, row in enumerate(table):
            row_text = ' '.join([str(cell) for cell in row if cell])
            
            # Look for summary data row: ['101', '015', '92,973.68', '102,181.34', '47,169.43 CR']
            # This row comes after the header with "Number of Withdrawals", "Number of Deposits", etc.
            if len(row) >= 5:
                # Check if this looks like the summary data row
                # First cell should be a number (withdrawal count)
                if row[0] and str(row[0]).strip().isdigit():
                    try:
                        num_w = int(row[0])
                        if 50 < num_w < 200:  # Reasonable range
                            summary['num_withdrawals'] = num_w
                            
                            # Parse deposits count
                            if row[1]:
                                deposit_str = str(row[1]).strip()
                                # Handle formats like "015" or "15"
                                summary['num_deposits'] = int(deposit_str)
                            
                            # Parse withdrawal total
                            if row[2] and ',' in str(row[2]):
                                summary['total_withdrawals'] = parse_amount(row[2])
                            
                            # Parse deposit total
                            if row[3] and ',' in str(row[3]):
                                summary['total_deposits'] = parse_amount(row[3])
                            
                            # Parse closing balance
                            if row[4] and 'CR' in str(row[4]):
                                summary['closing_balance'] = parse_amount(row[4])
                    except:
                        pass
            
            # Opening balance
            if 'Opening Balance' in row_text:
                for cell in row:
                    if cell and 'CR' in str(cell):
                        amount = parse_amount(cell)
                        if amount and amount > 1000 and summary['opening_balance'] is None:
                            summary['opening_balance'] = amount
    
    return summary

def parse_transactions_from_tables(tables):
    """Extract transactions from tables"""
    transactions = []
    transaction_num = 0
    prev_balance = None
    
    for table in tables:
        # Check if this is a transaction table by looking at header
        if not table or len(table) < 2:
            continue
        
        header = table[0]
        header_text = ' '.join([str(h) for h in header if h])
        
        # Look for transaction table header
        if 'Transaction Details' not in header_text:
            continue
        
        # Find column indices
        cols = {
            'date': -1,
            'value_date': -1,
            'details': -1,
            'withdrawals': -1,
            'deposits': -1,
            'balance': -1
        }
        
        for i, h in enumerate(header):
            if h:
                h_lower = h.lower()
                if 'date and time' in h_lower:
                    cols['date'] = i
                elif 'value date' in h_lower:
                    cols['value_date'] = i
                elif 'transaction details' in h_lower:
                    cols['details'] = i
                elif 'withdrawals' in h_lower:
                    cols['withdrawals'] = i
                elif 'deposits' in h_lower:
                    cols['deposits'] = i
                elif 'balance' in h_lower:
                    cols['balance'] = i
        
        # Parse data rows
        for row in table[1:]:
            if not row or len(row) < max(cols.values()) + 1:
                continue
            
            # Skip if no transaction details
            if cols['details'] < 0 or not row[cols['details']]:
                continue
            
            details = row[cols['details']]
            
            # Skip header rows or summary rows
            if 'Opening Balance' in details or 'Transaction Details' in details:
                continue
            
            # Extract data
            date_time = row[cols['date']] if cols['date'] >= 0 else None
            value_date = row[cols['value_date']] if cols['value_date'] >= 0 else None
            withdrawal = parse_amount(row[cols['withdrawals']]) if cols['withdrawals'] >= 0 else None
            deposit = parse_amount(row[cols['deposits']]) if cols['deposits'] >= 0 else None
            balance = parse_amount(row[cols['balance']]) if cols['balance'] >= 0 else None
            
            # Clean details - remove newlines but preserve full content
            details_clean = clean_text(details)
            
            # Skip if no amount or balance
            if not balance:
                continue
            
            # Determine amount and type
            if withdrawal:
                amount = withdrawal
                trans_type = 'DEBIT'
            elif deposit:
                amount = deposit
                trans_type = 'CREDIT'
            else:
                continue
            
            # BALANCE-BASED AUTO-CORRECTION
            # Calculate actual amount from balance difference
            if prev_balance is not None:
                if trans_type == 'CREDIT':
                    calculated_amount = balance - prev_balance
                else:
                    calculated_amount = prev_balance - balance
                
                # If calculated amount differs significantly, use it
                if abs(calculated_amount - amount) > 0.01:
                    print(f"  ⚠️  Transaction #{transaction_num + 1}: Parsed ₹{amount:.2f}, Balance math shows ₹{abs(calculated_amount):.2f}")
                    amount = abs(calculated_amount)
            
            transaction_num += 1
            transactions.append({
                'no': transaction_num,
                'date': parse_date(date_time) or parse_date(value_date),
                'merchant': extract_merchant(details_clean),
                'details': details_clean,  # Full details, cleaned
                'amount': round(amount, 2),
                'type': trans_type,
                'balance': round(balance, 2)
            })
            
            prev_balance = balance
    
    return transactions

def validate_transactions(transactions, summary):
    """Validate parsed transactions against summary"""
    credits = [t for t in transactions if t['type'] == 'CREDIT']
    debits = [t for t in transactions if t['type'] == 'DEBIT']
    
    total_credits = sum(t['amount'] for t in credits)
    total_debits = sum(t['amount'] for t in debits)
    
    validation = {
        'count_match': len(credits) == summary['num_deposits'] and len(debits) == summary['num_withdrawals'],
        'credits_match': abs(total_credits - summary['total_deposits']) < 0.01 if summary['total_deposits'] else False,
        'debits_match': abs(total_debits - summary['total_withdrawals']) < 0.01 if summary['total_withdrawals'] else False,
        'balance_match': abs(transactions[-1]['balance'] - summary['closing_balance']) < 0.01 if transactions and summary['closing_balance'] else False
    }
    
    validation['all_passed'] = all([
        validation['count_match'],
        validation['credits_match'],
        validation['debits_match'],
        validation['balance_match']
    ])
    
    return validation, {
        'expected_credits': summary['num_deposits'],
        'actual_credits': len(credits),
        'expected_debits': summary['num_withdrawals'],
        'actual_debits': len(debits),
        'expected_credit_total': summary['total_deposits'],
        'actual_credit_total': total_credits,
        'expected_debit_total': summary['total_withdrawals'],
        'actual_debit_total': total_debits
    }

def export_to_json(data, filename):
    """Export data to JSON file"""
    with open(filename, 'w') as f:
        json.dump(data, f, indent=2)
    print(f"\n✓ Exported to: {filename}")

def export_to_csv(transactions, filename):
    """Export transactions to CSV file"""
    import csv
    with open(filename, 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['no', 'date', 'merchant', 'details', 'amount', 'type', 'balance'])
        writer.writeheader()
        writer.writerows(transactions)
    print(f"✓ Exported to: {filename}")

def main():
    if len(sys.argv) < 3:
        print("Usage: python3 parse_with_tables.py <pdf_file> <password>")
        sys.exit(1)
    
    pdf_file = sys.argv[1]
    password = sys.argv[2]
    
    print(f"Parsing: {pdf_file}")
    print("=" * 60)
    
    # Extract tables
    tables = extract_tables_from_pdf(pdf_file, password)
    print(f"\nTotal tables extracted: {len(tables)}")
    
    # Parse summary
    summary = parse_summary_from_table(tables)
    print("\n" + "=" * 60)
    print("SUMMARY:")
    print(f"  Opening Balance: ₹{summary['opening_balance']:,.2f}" if summary['opening_balance'] else "  Opening Balance: Not found")
    print(f"  Closing Balance: ₹{summary['closing_balance']:,.2f}" if summary['closing_balance'] else "  Closing Balance: Not found")
    print(f"  Withdrawals: {summary['num_withdrawals']} = ₹{summary['total_withdrawals']:,.2f}" if summary['num_withdrawals'] and summary['total_withdrawals'] else "  Withdrawals: Not found")
    print(f"  Deposits: {summary['num_deposits']} = ₹{summary['total_deposits']:,.2f}" if summary['num_deposits'] and summary['total_deposits'] else "  Deposits: Not found")
    
    # Parse transactions
    print("\n" + "=" * 60)
    print("PARSING TRANSACTIONS...")
    transactions = parse_transactions_from_tables(tables)
    print(f"✓ Parsed {len(transactions)} transactions")
    
    # Validate
    print("\n" + "=" * 60)
    print("VALIDATION:")
    validation, details = validate_transactions(transactions, summary)
    
    expected_cr = details['expected_credits'] if details['expected_credits'] else '?'
    expected_dr = details['expected_debits'] if details['expected_debits'] else '?'
    expected_cr_total = f"₹{details['expected_credit_total']:,.2f}" if details['expected_credit_total'] else '?'
    expected_dr_total = f"₹{details['expected_debit_total']:,.2f}" if details['expected_debit_total'] else '?'
    expected_balance = f"₹{summary['closing_balance']:,.2f}" if summary['closing_balance'] else '?'
    
    print(f"  Credit Count: {details['actual_credits']} / {expected_cr} {'✓' if validation['count_match'] else '✗'}")
    print(f"  Debit Count: {details['actual_debits']} / {expected_dr} {'✓' if validation['count_match'] else '✗'}")
    print(f"  Credit Total: ₹{details['actual_credit_total']:,.2f} / {expected_cr_total} {'✓' if validation['credits_match'] else '✗'}")
    print(f"  Debit Total: ₹{details['actual_debit_total']:,.2f} / {expected_dr_total} {'✓' if validation['debits_match'] else '✗'}")
    print(f"  Final Balance: ₹{transactions[-1]['balance']:,.2f} / {expected_balance} {'✓' if validation['balance_match'] else '✗'}")
    
    if validation['all_passed']:
        print("\n✓ ALL VALIDATIONS PASSED")
    else:
        print("\n✗ VALIDATION FAILED")
    
    # Export
    base_name = pdf_file.replace('.pdf', '')
    
    # JSON export
    output_data = {
        'summary': summary,
        'transactions': transactions,
        'validation': {
            'passed': validation['all_passed'],
            'details': details
        }
    }
    export_to_json(output_data, f"{base_name}.json")
    
    # CSV export
    export_to_csv(transactions, f"{base_name}.csv")
    
    # Show first 10 transactions
    print("\n" + "=" * 60)
    print("SAMPLE TRANSACTIONS (first 10):")
    for t in transactions[:10]:
        print(f"{t['no']:3d}. {t['date']} | {t['merchant']:20s} | ₹{t['amount']:8.2f} {t['type']:6s} | Balance: ₹{t['balance']:,.2f}")

if __name__ == "__main__":
    main()
