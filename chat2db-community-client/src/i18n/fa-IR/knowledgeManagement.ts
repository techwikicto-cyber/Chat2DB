export default {
  'knowledgeManagement.title': 'مدیریت دانش',
  'knowledgeManagement.nav.terminology': 'واژگان تخصصی',
  'knowledgeManagement.terminology.description':
    'به Chat2DB کمک می‌کند توضیح هدف شما را در فرایند داده‌کاوی بهتر بفهمد. می‌توانید واژگان تخصصی خود را اینجا وارد کنید (امکان تعیین منبع داده وجود دارد، در غیر این صورت اثر سراسری دارد).',
  'knowledgeManagement.terminology.tips':
    'نام واژه: وام معوق، توضیح واژه: وامی که وام‌گیرنده نتواند آن را در بازه و مبلغ توافق‌شده بازپرداخت کند.',
  'knowledgeManagement.nav.businessLogic': 'منطق کسب‌وکار',
  'knowledgeManagement.businessLogic.description':
    'به Chat2DB کمک می‌کند منطق کسب‌وکار موجود در پایگاه‌داده شما را بهتر بفهمد. می‌توانید منطق را با جزئیات اینجا وارد کنید (امکان تعیین منبع داده وجود دارد، در غیر این صورت اثر سراسری دارد).',
  'knowledgeManagement.businessLogic.tips':
    'نام منطق کسب‌وکار: درآمد، توضیح منطق کسب‌وکار: فیلد status در جدول order_item برابر PAY_SUCCESS است و مبلغ سفارش باید بر ۱۰۰ تقسیم و تا دو رقم اعشار گرد شود.',
  'knowledgeManagement.nav.caseOptimization': 'بهینه‌سازی با نمونه',
  'knowledgeManagement.caseOptimization.description':
    'اگر دو روش بالا را امتحان کرده‌اید و خروجی هنوز مطلوب نیست، می‌توانید الگوهای SQL را برای هدایت خروجی مدل پیکربندی کنید (امکان تعیین منبع داده وجود دارد، در غیر این صورت اثر سراسری دارد).',
  'knowledgeManagement.caseOptimization.tips':
    'نام نمونه: کاربران جدید ۳۰ روز اخیر، محتوای نمونه: SELECT DATE(create_time) AS date, COUNT(*) AS new_users_count FROM user WHERE create_time >= CURDATE() - INTERVAL 30 DAY GROUP BY DATE(create_time) ORDER BY date;',
  'knowledgeManagement.tips.save': 'لطفاً ابتدا سطر فعلی را ذخیره کنید',
  'knowledgeManagement.tips.incomplete': 'لطفاً اطلاعات را کامل وارد کنید',
  'knowledgeManagement.label.knowledgeName': 'نام دانش',
  'knowledgeManagement.label.knowledgeContent': 'محتوای دانش',
  'knowledgeManagement.label.boundDataSource': 'منبع داده متصل',
  'knowledgeManagement.label.businessLogicName': 'نام منطق کسب‌وکار',
  'knowledgeManagement.label.businessLogicContent': 'محتوای منطق کسب‌وکار',
  'knowledgeManagement.label.caseOptimizationName': 'نام بهینه‌سازی با نمونه',
  'knowledgeManagement.label.caseOptimizationContent': 'محتوای بهینه‌سازی با نمونه',
  'knowledgeManagement.nav.annotationTable': 'حاشیه‌نویسی جدول‌های پایگاه‌داده',
  'knowledgeManagement.annotationTable.description':
    'Chat2DB امکان حاشیه‌نویسی دقیق فیلدهای جدول‌های پایگاه‌داده را فراهم می‌کند. این کار به هوش مصنوعی کمک می‌کند معنای هر فیلد را بهتر بفهمد و دقت پردازش و تحلیل داده را بالا ببرد.',
  'knowledgeManagement.label.batchOperation': 'عملیات گروهی',
  'knowledgeManagement.label.batchImport': 'افزودن گروهی',
  'knowledgeManagement.label.batchExport': 'برون‌بری گروهی',
  'knowledgeManagement.label.batchDelete': 'حذف گروهی',
  'knowledgeManagement.label.batchDeleteConfirm': 'از حذف {1} رکورد انتخاب‌شده مطمئن هستید؟',
  'knowledgeManagement.label.batchExportAll': 'برون‌بری گروهی همه',
  'knowledgeManagement.label.downloadTemplate': 'دانلود قالب',
  'knowledgeManagement.tips.select': 'لطفاً ابتدا سطر موردنظر برای عملیات را انتخاب کنید',
  'knowledgeManagement.tips.importSuccess': '{1} مورد با موفقیت درون‌ریزی شد',
  'knowledgeManagement.tips.exportSuccess': '{1} مورد با موفقیت برون‌بری شد',
  'knowledgeManagement.tips.deleteSuccess': '{1} مورد با موفقیت حذف شد',
};
